package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.InstanceRequest
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.test.utils.*
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

@Suppress("UNUSED_VARIABLE")
class EngineSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a two layer workflow") {
        group("after finishing the sub-workflow") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getTwoLayerWorkflow(kplan), input = Instance.Input(0, "", Unit))
                start()

                moveWithUnit("A", kplan, instance)
                moveWithUnit("B", kplan, instance)

                val sub = kplan.findInstances(InstanceRequest(genericSubWorkflowName, root = false), 10).first.first()

                moveWithUnit("D", kplan, sub)
                moveWithUnit("E", kplan, sub)

                Pair(kplan, instance)
            }

            it("should show five states") {
                val (kplan, instance) = setup()

                val states = kplan.getRecursiveStatesOfRoot(rootInstanceId = instance.info.id, businessOnly = true)

                states.size shouldMatch equalTo(5)

                states shouldMatch elements(
                        hasTask("A"),
                        hasTask("B"),
                        hasTask("D"),
                        hasTask("E"),
                        hasTask("F")
                )
            }

        }
    }

})