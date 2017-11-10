package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.InstanceRequest
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.test.utils.*
import com.dentalmonitoring.kplan.workflow.KPEventBus
import com.dentalmonitoring.kplan.model.Link
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

@Suppress("UNUSED_VARIABLE")
class SubWorkflowSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a simple manual linear 2-stages workflow") {

        group("on creation") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getTwoLayerWorkflow(kplan), input = Instance.Input(0, "", Unit))

                moveWithUnit("A", kplan, instance)
                moveWithUnit("B", kplan, instance)

                Triple(kplan, instance, start)
            }

            it("should show the parent instances without business states") {
                val (kplan, instance, start) = setup()
                start()

                val (instances, offsetKey) = kplan.findInstances(InstanceRequest(twoLayerWorkflowName), 10, null)
                instances shouldMatch oneElement(
                    has(Instance<*>::info, has(Instance.Info::workflowName, equalTo(twoLayerWorkflowName)))
                )
                offsetKey shouldMatch absent()

                kplan.getRunningBusinessStates(instances[0].info.id) shouldMatch isEmpty
            }

            it("should show the child instances with a states") {
                val (kplan, instance, start) = setup()
                start()

                val subWorkflow = kplan.workflows[genericSubWorkflowName]
                subWorkflow shouldMatch present(anything)

                val (instances, offsetKey) = kplan.findInstances(InstanceRequest(subWorkflow!!.name, root = false), 10, null)
                instances shouldMatch oneElement(
                    has(Instance<*>::info, has(Instance.Info::workflowName, equalTo(genericSubWorkflowName)))
                )
                offsetKey shouldMatch absent()

                kplan.getRunningBusinessStates(instances[0].info.id) shouldMatch oneElement(
                        hasTask("D")
                )
            }

        }

        group("on move") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getTwoLayerWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                moveWithUnit("A", kplan, instance)
                moveWithUnit("B", kplan, instance)

                val sub = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10).first.first()

                moveWithUnit("D", kplan, sub)
                moveWithUnit("E", kplan, sub)

                kplan
            }

            it("should end the sub instance") {
                val kplan = setup()

                val subWorkflow = kplan.workflows[genericSubWorkflowName]
                subWorkflow shouldMatch present(anything)

                val (instances, offsetKey) = kplan.findInstances(InstanceRequest(subWorkflow!!.name), 10, null)
                instances shouldMatch isEmpty
                offsetKey shouldMatch absent()
            }

            it("should show the parent instances with a business states") {
                val kplan = setup()

                val (instances, offsetKey) = kplan.findInstances(InstanceRequest(twoLayerWorkflowName), 10, null)
                instances shouldMatch oneElement(
                    has(Instance<*>::info, has(Instance.Info::workflowName, equalTo(twoLayerWorkflowName)))
                )
                offsetKey shouldMatch absent()

                kplan.getRunningBusinessStates(instances[0].info.id) shouldMatch oneElement(
                        hasTask("F")
                )
            }

        }

    }

    given("a workflow with an interruptible sub workflow") {

        group("on interruption") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getTwoLayerInterruptWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                moveWithUnit("A", kplan, instance)
                moveWithUnit("B", kplan, instance)

                val sub = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10).first.first()

                Triple(kplan, instance, sub)
            }

            it("should interrupt the sub workflow") {
                val (kplan, instance, sub) = setup()

                var sPassed = false
                var iPassed = false
                (kplan.eventBus as KPLocalEventBus).register(sub.info.id) {
                    // TODO: check if other events should throw exception, if not: replace when by if
                    when (it) {
                        is KPEventBus.Event.Instance.Running.End -> {
                            it shouldMatch has(KPEventBus.Event.Instance.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                            iPassed = true
                        }
                        is KPEventBus.Event.State.Running.End -> {
                            it shouldMatch has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.INTERRUPTION))
                            sPassed = true
                        }
                    }
                }

                kplan.getMove(sub.info).interrupt("interrupt", Unit)

                sPassed shouldMatch isTrue
                iPassed shouldMatch isTrue
            }

            it("should show one state") {
                val (kplan, instance, sub) = setup()
                kplan.getMove(sub.info).interrupt(genericInterrupt, Unit)

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("F")
                )
            }

        }

    }

    given("a parallel 2-stages workflow") {

        group("on start") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getParallelTwoLayerWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                Pair(kplan, instance)
            }

            it("should show both states when searching recursively") {
                val (kplan, instance) = setup()

                kplan.getRecursiveRunningBusinessStates(instance.info.id) shouldMatch elements(
                        hasTask("A"),
                        hasTask("C")
                )
            }

        }
    }
})