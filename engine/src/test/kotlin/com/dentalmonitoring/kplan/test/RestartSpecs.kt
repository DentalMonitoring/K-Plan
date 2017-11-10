package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.InstanceRequest
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.model.StateId
import com.dentalmonitoring.kplan.test.utils.*
import com.dentalmonitoring.kplan.workflow.KPEventBus
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.should.shouldMatch
import com.natpryce.hamkrest.should.shouldNotMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.util.*

@Suppress("UNUSED_VARIABLE")
class RestartSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a simple manual linear workflow") {

        group("on simple restart") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleManualLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))

                val run: () -> StateId = {
                    start()
                    val a = moveWithString("A", kplan, instance)
                    moveWithString("B", kplan, instance)
                    kplan.restartAt(a)
                    a
                }

                Triple(kplan, instance, run)
            }

            it("should show the first state only") {
                val (kplan, instance, run) = setup()
                run()

                val list = kplan.getStatesOf(instance.info.id)
                list shouldMatch oneElement(
                        hasTask("A")
                )
            }

            it("should have a new state id for the restarted task") {
                val (kplan, instance, run) = setup()
                val uuidBeforeRestart = run()

                val list = kplan.getStatesOf(instance.info.id)
                list shouldMatch oneElement(
                        hasTask("A")
                )
                list.first() shouldNotMatch has(
                    State<*>::info, has(State.Info::id, equalTo(uuidBeforeRestart))
                )
            }

            it("should fire the proper event sequence") {
                val (kplan, instance, run) = setup()

                val list = LinkedList<KPEventBus.Event>()
                (kplan.eventBus as KPLocalEventBus).register(instance.info.id) {
                    list += it
                }

                run()

                list shouldMatch elements(
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.FLOW))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("B"))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("B"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.FLOW))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("C"))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("C"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("B"))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                    )
                )
            }
        }

        group("on restart a running state") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleManualLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))

                val run: () -> Unit = {
                    start()

                    moveWithString("A", kplan, instance)
                    val b = kplan.getRunningBusinessStates(instance.info.id).first()
                    kplan.restartAt(b.info.id)
                }

                Triple(kplan, instance, run)
            }

            it("should fail") {
                val (kplan, instance, run) = setup()
                run shouldMatch throws<IllegalArgumentException>()
            }
        }
    }

    given("a parallel workflow") {

        fun baseSetup() = run {
            val kplan = helper.newKplan()
            val (instance, start) = kplan.create(workflow = getSimpleParallelWorkflow(kplan), input = Instance.Input(0, "", Unit))
            start()

            val a = kplan.getRunningBusinessStates(instance.info.id).first()
            kplan.getMove(a.info).flow("one")
            val par = kplan.getRunningBusinessStates(instance.info.id)
            par shouldMatch has("size", Collection<*>::size, equalTo(2))
            val b = par.first { it.info.taskName == "B" }
            val c = par.first { it.info.taskName == "C" }
            par.forEach { kplan.getMove(it.info).flow("two") }
            val list = kplan.getRunningBusinessStates(instance.info.id)
            list shouldMatch oneElement(has(State<*>::taskName, equalTo("D")))
            val d = list.first()

            Triple(kplan, instance, mapOf("a" to a, "b" to b, "c" to c, "d" to d))
        }

        group("on restart before parallel") {

            fun setup() = run {
                val (kplan, instance, map) = baseSetup()

                kplan.restartAt(map["a"]!!.info.id)

                Pair(kplan, instance)
            }

            it("should show the first state only") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(has(State<*>::taskName, equalTo("A")))
            }
        }

        group("on restart inside parallel") {

            fun setup() = run {
                val (kplan, instance, map) = baseSetup()

                kplan.restartAt(map["b"]!!.info.id)

                Pair(kplan, instance)
            }

            it("should show the restarted state only") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(has(State<*>::taskName, equalTo("B")))
            }

            it("should open the barrier when the restarted state ends") {
                val (kplan, instance) = setup()

                val b = kplan.getRunningBusinessStates(instance.info.id).first()
                kplan.getMove(b.info).flow("three")

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                    has(State<*>::taskName, equalTo("D"))
                        and has(State<*>::input, equalTo(mapOf("B" to "three", "C" to "two") as Any))
                )
            }
        }
    }

    given("a two layer workflow") {

        group("on restart before workflow") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getTwoLayerWorkflow(kplan), input = Instance.Input(0, "", Unit))
                val run = {
                    start()

                    val a = moveWithUnit("A", kplan, instance)
                    moveWithUnit("B", kplan, instance)
                    val sub = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10).first.first()
                    moveWithUnit("D", kplan, sub)

                    kplan.restartAt(a)
                }

                Triple(kplan, instance, run)
            }

            it("should show the first state only") {
                val (kplan, instance, run) = setup()
                run()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(has(State<*>::info,
                    has(State.Info::taskName, equalTo("A"))
                        and has(State.Info::endDate, equalTo(null as Date?))
                ))
            }

            it("should show no sub instance") {
                val (kplan, instance, run) = setup()
                run()

                val (list) = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10)
                list shouldMatch isEmpty
            }

            it("should fire the proper event sequence") {
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
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 9
                        has(KPEventBus.Event.State::stateTaskName, equalTo("C"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                            and has(KPEventBus.Event.State.Running.End::pastSaved, isFalse)
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 10
                        has(KPEventBus.Event.State::stateTaskName, equalTo("E"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                            and has(KPEventBus.Event.State.Running.End::pastSaved, isFalse)
                    ),
                    cast<KPEventBus.Event.Instance.Running.End>("Instance.End", // 11
                        has(KPEventBus.Event.Instance::instanceWorkflowName, equalTo(genericSubWorkflowName))
                            and has(KPEventBus.Event.Instance.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                            and has(KPEventBus.Event.Instance.Running.End::pastSaved, isFalse)
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 12
                        has(KPEventBus.Event.State::stateTaskName, equalTo("D"))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 13
                        has(KPEventBus.Event.State::stateTaskName, equalTo("B"))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 14
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 15
                        has(KPEventBus.Event.State::stateTaskName, equalTo("A"))
                    )
                )
            }
        }

        group("on restart during workflow") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getTwoLayerWorkflow(kplan), input = Instance.Input(0, "", Unit))
                val run = {
                    start()

                    moveWithUnit("A", kplan, instance)
                    moveWithUnit("B", kplan, instance)

                    val sub = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10).first.first()

                    val d = moveWithUnit("D", kplan, sub)
                    moveWithUnit("E", kplan, sub)

                    kplan.restartAt(d)
                }

                Triple(kplan, instance, run)
            }

            it("should show the restarted state and the technical subworkflow state") {
                val (kplan, instance, run) = setup()
                run()

                kplan.getRecursiveRunningStates(instance.info.id) shouldMatch elements(
                    has(State<*>::info, has(State.Info::taskName, equalTo("C")) and has(State.Info::endDate, equalTo(null as Date?))),
                    has(State<*>::info, has(State.Info::taskName, equalTo("D")) and has(State.Info::endDate, equalTo(null as Date?)))
                )
            }

            it("should show the sub instance") {
                val (kplan, instance, run) = setup()
                run()

                val (instances) = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10)
                instances shouldMatch oneElement(has(Instance<*>::info, has(Instance.Info::workflowName, equalTo(genericSubWorkflowName))))
            }

            it("should fire the proper event sequence") {
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
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 9
                        has(KPEventBus.Event.State::stateTaskName, equalTo("E"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.FLOW))
                    ),
                    cast<KPEventBus.Event.Instance.Running.End>("Instance.End", // 10
                        has(KPEventBus.Event.Instance::instanceWorkflowName, equalTo(genericSubWorkflowName))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 11
                        has(KPEventBus.Event.State::stateTaskName, equalTo("C"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.FLOW))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 12
                        has(KPEventBus.Event.State::stateTaskName, equalTo("F"))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 13
                        has(KPEventBus.Event.State::stateTaskName, equalTo("Z"))
                    ),
                    cast<KPEventBus.Event.State.Running.End>("Running.End", // 14
                        has(KPEventBus.Event.State::stateTaskName, equalTo("F"))
                            and has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                            and has(KPEventBus.Event.State.Running.End::pastSaved, isFalse)
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 15
                        has(KPEventBus.Event.State::stateTaskName, equalTo("Z"))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 16
                        has(KPEventBus.Event.State::stateTaskName, equalTo("E"))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 17
                        has(KPEventBus.Event.State::stateTaskName, equalTo("C"))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 18
                        has(KPEventBus.Event.State::stateTaskName, equalTo("C"))
                    ),
                    cast<KPEventBus.Event.Instance.PastRemoved>("PastRemoved", // 19
                        has(KPEventBus.Event.Instance::instanceWorkflowName, equalTo(genericSubWorkflowName))
                    ),
                    cast<KPEventBus.Event.Instance.Running.Start>("Instance.Start", // 20
                        has(KPEventBus.Event.Instance::instanceWorkflowName, equalTo(genericSubWorkflowName))
                    ),
                    cast<KPEventBus.Event.State.PastRemoved>("PastRemoved", // 21
                        has(KPEventBus.Event.State::stateTaskName, equalTo("D"))
                    ),
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start", // 22
                        has(KPEventBus.Event.State::stateTaskName, equalTo("D"))
                    )
                )
            }
        }
    }


})