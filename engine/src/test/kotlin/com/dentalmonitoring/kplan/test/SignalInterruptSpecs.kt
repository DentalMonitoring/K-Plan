package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.test.utils.*
import com.dentalmonitoring.kplan.workflow.KPEventBus
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

class SignalInterruptSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a simple workflow with a signal") {

        group("on signal") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleSignalWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                signal("A", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show two running states") {
                val (kplan, instance) = setup()

                kplan.getStatesOf(instance.info.id) shouldMatch elements(
                        hasTask("A"),
                        hasTask("B")
                )
            }

            it("should show the signal input") {
                val (kplan, instance) = setup()

                val stateB = kplan.getStatesOf(instance.info.id).first { it.info.taskName == "B" }

                stateB shouldMatch has(State<*>::input, equalTo(genericSignalInput as Any))
            }

        }

        group("on signal moved") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleSignalWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                signal("A", kplan, instance)
                moveWithUnit("B", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show one running state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("A")
                )
            }

            it("should show one past state") {
                val (kplan, instance) = setup()

                kplan.getPastStates(instance.info.id) shouldMatch oneElement(
                        hasTask("B")
                )
            }

        }

        group("on workflow ended") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleSignalWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                signal("A", kplan, instance)

                Pair(kplan, instance)
            }

            it("should interrupt the signal state") {
                val (kplan, instance) = setup()

                var passed = false

                (kplan.eventBus as KPLocalEventBus).register(instance.info.id) {
                    if (it is KPEventBus.Event.State && it.state.info.taskName == "B") {
                        it shouldMatch cast<KPEventBus.Event.State.Running.End>(has(KPEventBus.Event.State.Running.End::linkType, equalTo(Link.Type.INTERRUPTION)))
                        passed = true
                    }
                }

                moveWithUnit("A", kplan, instance)

                passed shouldMatch isTrue
            }

            it("should show three past states") {
                val (kplan, instance) = setup()

                moveWithUnit("A", kplan, instance)

                kplan.getPastStates(instance.info.id) shouldMatch elements(
                        hasTask("A"),
                        hasTask("B"),
                        hasTask("Z")
                )
            }
        }

    }

    given("a simple workflow with an interruptible task") {

        group("on interruption") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleInterruptWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                interrupt("A", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show one running states") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("B")
                )
            }

            it("should show the interruption input") {
                val (kplan, instance) = setup()

                val stateB = kplan.getStatesOf(instance.info.id).first { it.info.taskName == "B" }

                stateB shouldMatch has(State<*>::input, equalTo(genericInterruptInput as Any))
            }

        }


    }

})
