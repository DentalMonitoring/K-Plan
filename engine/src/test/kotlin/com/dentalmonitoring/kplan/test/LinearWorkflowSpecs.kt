package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.InstanceRequest
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.model.RunningState
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.test.utils.*
import com.dentalmonitoring.kplan.workflow.KPEventBus
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

@Suppress("UNUSED_VARIABLE")
class LinearWorkflowSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a simple manual linear workflow") {

        group("on creation") {
            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleManualLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))

                Triple(kplan, instance, start)
            }

            it("should show an instance and a state") {
                val (kplan, instance, start) = setup()
                start()

                val (instances, offsetKey) = kplan.findInstances(InstanceRequest(wfName = simpleManualLinearWorkflowName), 10, null)
                instances shouldMatch oneElement(
                    has(Instance<*>::info, has(Instance.Info::workflowName, equalTo(simpleManualLinearWorkflowName)))
                )

                offsetKey shouldMatch absent()

                val states = kplan.getStatesOf(instance.info.id)
                states shouldMatch oneElement(
                        hasTask("A")
                )
            }

            it("should dispatch start events") {
                val (kplan, instance, start) = setup()

                var passed = false

                (kplan.eventBus as KPLocalEventBus).register(KPLocalEventBus.Registration(instance.info.id, null) {
                    it shouldMatch cast<KPEventBus.Event.State.Running.Start>(
                        has(KPEventBus.Event::instances, equalTo(listOf<Instance<*>>(instance)))
                            and has(KPEventBus.Event.State::state, has(State<*>::info, has(State.Info::taskName, equalTo("A"))))
                    )
                    passed = true
                })

                start()

                passed shouldMatch isTrue
            }

        }

        group("on move") {
            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleManualLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                Pair(kplan, instance)
            }

            it("should show the new instance") {
                val (kplan, instance) = setup()

                moveWithString("A", kplan, instance)

                val (list, offsetKey) = kplan.findInstances(InstanceRequest(wfName = simpleManualLinearWorkflowName), 10, null)
                list shouldMatch oneElement(
                    has(Instance<*>::info,
                        has(Instance.Info::workflowName, equalTo(simpleManualLinearWorkflowName))
                            and has(Instance.Info::endDate, absent())
                    )
                )
                offsetKey shouldMatch absent()
            }

            it("should dispatch state move events") {
                val (kplan, instance) = setup()

                val state = kplan.getStatesOf(instance.info.id).first()

                var passedA = false
                var passedB = false

                (kplan.eventBus as KPLocalEventBus).register(KPLocalEventBus.Registration(instance.info.id, null) {
                    it as KPEventBus.Event.State
                    when (it.state.info.taskName) {
                        "A" -> {
                            it shouldMatch cast<KPEventBus.Event.State.Running.End>(
                                has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.FLOW))
                            )
                            passedA = true
                        }
                        "B" -> {
                            it shouldMatch cast<KPEventBus.Event.State.Running.Start>(anything)
                            passedB = true
                        }
                        else -> throw IllegalArgumentException()
                    }
                })

                state.info.taskName shouldMatch equalTo("A")

                kplan.getMove(state.info).flow("string")

                passedA shouldMatch isTrue
                passedB shouldMatch isTrue
            }

            it("should have only one running state") {
                val (kplan, instance) = setup()

                moveWithString("A", kplan, instance)

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("B")
                )
            }
        }

        group("on move to the end") {
            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleManualLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                Pair(kplan, instance)
            }

            it("should show no running instance") {
                val (kplan, instance) = setup()

                moveWithString("A", kplan, instance)
                moveWithString("B", kplan, instance)
                moveWithUnit("C", kplan, instance)

                val (list, offsetKey) = kplan.findInstances(InstanceRequest(wfName = simpleManualLinearWorkflowName), 10, null)
                list shouldMatch isEmpty
                offsetKey shouldMatch absent()
            }

            it("should show the passed instance") {
                val (kplan, instance) = setup()

                moveWithString("A", kplan, instance)
                moveWithString("B", kplan, instance)
                moveWithUnit("C", kplan, instance)

                val (list, offsetKey) = kplan.findInstances(InstanceRequest(wfName = simpleManualLinearWorkflowName, running = RunningState.ONLY_PAST), 10, null)
                list shouldMatch oneElement(
                    has(Instance<*>::info,
                        has(Instance.Info::workflowName, equalTo(simpleManualLinearWorkflowName))
                            and has(Instance.Info::endDate, present(anything))
                    )
                )
                offsetKey shouldMatch absent()
            }

            it("should dispatch end instance events") {
                val (kplan, instance) = setup()

                var passed = false
                (kplan.eventBus as KPLocalEventBus).register(KPLocalEventBus.Registration(instance.info.id) {
                    if (it is KPEventBus.Event.Instance.Running.End) {
                        it.instance.info.id shouldMatch equalTo(instance.info.id)
                        passed = true
                    }
                })

                moveWithString("A", kplan, instance)
                moveWithString("B", kplan, instance)
                moveWithUnit("C", kplan, instance)

                passed shouldMatch isTrue
            }
        }
    }

    given("a simple automatic linear workflow") {

        group("on creation") {
            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleAutomaticLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                Pair(kplan, instance)
            }

            it("should advance to second state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                    hasTask("B")
                        and has(State<*>::input, equalTo("string" as Any))
                )
            }

            it("should show the first past instance") {
                val (kplan, instance) = setup()

                kplan.getPastStates(instance.info.id) shouldMatch oneElement(
                    has(State<*>::info, has(State.Info::taskName, equalTo("A")))
                )
            }
        }

        group("on move") {
            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleAutomaticLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                val state = kplan.getRunningBusinessStates(instance.info.id).first()
                kplan.getMove(state.info).flow("string")

                kplan
            }

            it("should end the instance") {
                val kplan = setup()

                val (list, offsetKey) = kplan.findInstances(InstanceRequest(wfName = simpleAutomaticLinearWorkflowName, running = RunningState.ONLY_PAST), 10, null)
                list shouldMatch oneElement(
                    has(Instance<*>::info,
                        has(Instance.Info::workflowName, equalTo(simpleAutomaticLinearWorkflowName))
                            and has(Instance.Info::endDate, present(anything))
                    )
                )
                offsetKey shouldMatch absent()
            }
        }
    }

})