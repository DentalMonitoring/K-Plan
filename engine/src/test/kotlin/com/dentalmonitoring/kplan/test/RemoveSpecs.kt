package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.InstanceRequest
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.test.utils.*
import com.dentalmonitoring.kplan.workflow.KPEventBus
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.util.*

@Suppress("UNUSED_VARIABLE")
class RemoveSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a two layer workflow") {

        group("on remove during a subworkflow") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getTwoLayerWorkflow(kplan), input = Instance.Input(0, "", Unit))
                val run = {
                    start()

                    moveWithUnit("A", kplan, instance)
                    moveWithUnit("B", kplan, instance)

                    val sub = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10).first.first()

                    moveWithUnit("D", kplan, sub)

                    kplan.removeInstance(instance.info.id)
                    sub
                }

                Triple(kplan, instance, run)
            }

            it("should remains no instances") {
                val (kplan, instance, run) = setup()
                run()

                val (list) = kplan.findInstances(InstanceRequest(), 10) // Find anything
                list shouldMatch isEmpty
            }

            it("should remains no states") {
                val (kplan, instance, run) = setup()
                val subInstance = run()

                val rootStates = kplan.getStatesOf(instance.info.id, false)
                rootStates shouldMatch isEmpty

                val subInstanceStates = kplan.getStatesOf(subInstance.info.id, false)
                subInstanceStates shouldMatch isEmpty
            }

            // TODO:
//            it("should remains no links") {
//            }

            it("should fire the proper event sequence for interruption") {
                val (kplan, instance, run) = setup()

                val list = LinkedList<KPEventBus.Event>()
                (kplan.eventBus as KPLocalEventBus).register(instance.info.id) {
                    list += it
                }

                run()

                list shouldMatch elements(
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 0
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 1
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.FLOW))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 2
                        has(KPEventBus.Event.State::stateTaskName, equalTo("B"))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 3
                        has(KPEventBus.Event.State::stateTaskName, equalTo("B"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.FLOW))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 4
                        has(KPEventBus.Event.State::stateTaskName, equalTo("C"))
                    ),
                    cast<KPEventBus.Event.Instance.Running.Start>("Instance.Start", // 5
                        has(KPEventBus.Event.Instance::instanceWorkflowName, equalTo(genericSubWorkflowName))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 6
                        has(KPEventBus.Event.State::stateTaskName, equalTo("D"))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 7
                        has(KPEventBus.Event.State::stateTaskName, equalTo("D"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.FLOW))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 8
                        has(KPEventBus.Event.State::stateTaskName, equalTo("E"))
                    ),
                    // WHEN REMOVING ALL
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 9
                        has(KPEventBus.Event.State::stateTaskName, equalTo("C"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 10
                        has(KPEventBus.Event.State::stateTaskName, equalTo("E"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                    ),
                    cast<KPEventBus.Event.Instance.Running.End>("Instance.End", // 11
                        has(KPEventBus.Event.Instance::instanceWorkflowName, equalTo(genericSubWorkflowName))
                            and has(KPEventBus.Event.Instance.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                            and has(KPEventBus.Event.Instance.Running.End::pastSaved, isFalse)
                    ),
                    cast<KPEventBus.Event.Instance.Running.End>("Instance.End", // 12
                        has(KPEventBus.Event.Instance::instanceWorkflowName, equalTo(twoLayerWorkflowName))
                            and has(KPEventBus.Event.Instance.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                            and has(KPEventBus.Event.Instance.Running.End::pastSaved, isFalse)
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 13
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 14
                        has(KPEventBus.Event.State::stateTaskName, equalTo("B"))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 15
                        has(KPEventBus.Event.State::stateTaskName, equalTo("D"))
                    )
                )
            }
        }

        group("on remove a non-root workflow") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getTwoLayerWorkflow(kplan), input = Instance.Input(0, "", Unit))
                val run = {
                    start()

                    moveWithUnit("A", kplan, instance)
                    moveWithUnit("B", kplan, instance)

                    val sub = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10).first.first()

                    moveWithUnit("D", kplan, sub)
                    val d = kplan.getRunningBusinessStates(sub.info.id).first()

                    kplan.removeInstance(sub.info.id)
                }

                Triple(kplan, instance, run)
            }

            it("should fail") {
                val (_, _, run) = setup()
                run shouldMatch throws<IllegalArgumentException>()
            }
        }
    }
})