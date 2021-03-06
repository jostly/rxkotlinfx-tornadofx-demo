package view

import app.Styles
import app.alertError
import app.toSet
import domain.Customer
import javafx.event.ActionEvent
import javafx.geometry.Orientation
import javafx.scene.control.TableView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import rx.Observable
import rx.javafx.kt.*
import rx.lang.kotlin.filterNotNull
import rx.lang.kotlin.subscribeWith
import rx.lang.kotlin.toObservable
import tornadofx.*

class AppliedCustomerView : View() {
    override val root = BorderPane()

    private val controller: EventController by inject()
    private var table: TableView<Customer> by singleAssign()

    init {
        with(root) {

            top = label("ASSIGNED CUSTOMERS").addClass(Styles.heading)

            center = tableview<Customer> {
                column("ID", Customer::id)
                column("Name", Customer::name)

                //broadcast selections
                selectionModel.selectedItems.onChangedObservable()
                    .flatMap { it.toObservable().filterNotNull().map { it.id }.toSet() }
                    .addTo(controller.selectedApplications)

                //subscribe to selections in SalesPeopleView extract a list of customers
                val selectedIds = selectionModel.selectedItems.onChangedObservable().filterNotNull()
                        .filter { it[0] != null }
                        .flatMap { it.toObservable().map { it.id }.distinct().toSet() }
                        .filterNotNull()
                        .toBinding()

                //if multiple SalesPeople are selected, we consolidate their customers distinctly.
                //Otherwise we will push out a hot list of Customers for that one SalesPerson.
                //It will update automatically and the switchMap() will kill it when the selection changes
                controller.selectedSalesPeople.toObservable()
                    .switchMap { selectedPeople ->
                        //the switchMap() is raw power! it unsubscribes the previous emission when a new one comes in

                        if (selectedPeople.size == 1) {
                            selectedPeople.toObservable().flatMap {
                                it.customerAssignments.onChangedObservable()
                                    .switchMap {
                                        it.toObservable().flatMap { Customer.forId(it) }.toList()
                                    }
                            }
                        } else {
                            selectedPeople.toObservable().flatMap { it.customerAssignments.toObservable() }
                                    .distinct()
                                    .flatMap { Customer.forId(it) }
                                    .toSortedList { x,y -> x.id.compareTo(y.id) }
                        }
                    }.filterNotNull().subscribeWith {
                        onNext {
                            items.setAll(it)
                            selectWhere { it.id in selectedIds.value?:setOf() }
                            requestFocus()
                            resizeColumnsToFitContent()
                        }
                        alertError()
                    }

                table = this
            }
            left = toolbar {
                orientation = Orientation.VERTICAL
                button("▲") {
                    tooltip("Move customer up (CTRL + ↑)")

                    //disable when multiple salespeople selected
                    controller.selectedSalesPeople.toObservable().map { it.size > 1 }.subscribe { isDisable = it }

                    //broadcast move up requests

                    val keyEvents =  table.events(KeyEvent.KEY_PRESSED).filter { it.isControlDown && it.code == KeyCode.UP }
                    val buttonEvents = actionEvents()

                    Observable.merge(keyEvents, buttonEvents)
                            .map { table.selectedItem?.id }
                            .filterNotNull()
                            .addTo(controller.moveCustomerUp)

                    useMaxWidth = true
                }
                button("▼") {
                    tooltip("Move customer down (CTRL + ↓)")

                    //disable when multiple salespeople selected
                    controller.selectedSalesPeople.toObservable().map { it.size > 1 }.subscribe { isDisable = it }

                    //broadcast move down requests
                    val keyEvents =  table.events(KeyEvent.KEY_PRESSED).filter { it.isControlDown && it.code == KeyCode.DOWN }
                    val buttonEvents = actionEvents()

                    Observable.merge(keyEvents, buttonEvents)
                        .map { table.selectedItem?.id }.filterNotNull().addTo(controller.moveCustomerDown)

                    useMaxWidth = true
                }
                button("\uD83D\uDD0E⇉") {
                    actionEvents().flatMap {
                        controller.selectedApplications.toObservable().take(1)
                    }.addTo(controller.searchCustomers)
                }
            }
        }
    }
}