package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.test.utils.*
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

class ParallelWorkflowSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a simple parallel workflow") {

        group("on start") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleParallelWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                Pair(kplan, instance)
            }

            it("should show one state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("A")
                )
            }

        }

        group("on move A") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleParallelWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                moveWithString("A", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show two states") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch elements(
                        hasTask("B"),
                        hasTask("C")
                )
            }

        }

        group("on move A and B") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleParallelWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                moveWithString("A", kplan, instance)
                moveWithString("B", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show one running state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("C")
                )
            }

            it("should show two past states") {
                val (kplan, instance) = setup()

                kplan.getPastStates(instance.info.id) shouldMatch elements(
                        hasTask("A"),
                        hasTask("!${genericParallelName}"),
                        hasTask("B")
                )
            }

        }

        group("on move A and B and C") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleParallelWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                moveWithString("A", kplan, instance)
                moveWithString("B", kplan, instance)
                moveWithString("C", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show one running state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                    hasTask("D")
                        and hasTaskMap(mapOf("B" to "Out-B", "C" to "Out-C"))
                )
            }

            it("should show four past states") {
                val (kplan, instance) = setup()

                kplan.getPastStates(instance.info.id) shouldMatch elements(
                        hasTask("A"),
                        hasTask("!${genericParallelName}"),
                        hasTask("B"),
                        hasTask("C"),
                        hasTask("/${genericParallelName}")
                )
            }

        }

    }

    given("a workflow with a signal and a barrier") {

        group("on signal A and move A") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSignalAndBarrierWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                signal("A", kplan, instance)
                moveWithUnit("A", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show B running state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("B")
                )
            }

        }

        group("on signal A and move B") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSignalAndBarrierWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                signal("A", kplan, instance)
                moveWithUnit("B", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show A running state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("A")
                )
            }

        }

        group("on signal A and move A and B") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSignalAndBarrierWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                signal("A", kplan, instance)
                moveWithUnit("A", kplan, instance)
                moveWithUnit("B", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show C running state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("C")
                )
            }

        }

        group("on move without signal") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSignalAndBarrierWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                moveWithUnit("A", kplan, instance)

                Pair(kplan, instance)
            }

            it("should show C running state") {
                val (kplan, instance) = setup()

                kplan.getRunningBusinessStates(instance.info.id) shouldMatch oneElement(
                        hasTask("C")
                )
            }

        }

    }

})
