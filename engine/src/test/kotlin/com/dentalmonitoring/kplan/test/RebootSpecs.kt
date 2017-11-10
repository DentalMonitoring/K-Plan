package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.test.utils.KPLocalEventBus
import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.RunningState
import com.dentalmonitoring.kplan.test.utils.KPlanSpecsHelper
import com.dentalmonitoring.kplan.test.utils.genericParallelName
import com.dentalmonitoring.kplan.test.utils.getComplextWorkflow
import com.dentalmonitoring.kplan.test.utils.getSimpleManualLinearWorkflow
import com.dentalmonitoring.kplan.workflow.KPEventBus
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.util.*

@Suppress("UNUSED_VARIABLE")
class RebootSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a simple manual linear workflow") {

        group("on reboot") {

            it("should show the first state only") {

                val kplan = helper.newKplan()
                var instanceId: InstanceId? = null

                run {
                    val (instance, start) = kplan.create(workflow = getSimpleManualLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))

                    instanceId = instance.info.id

                    var passed: String? = null
                    val iid = instanceId
                    if (iid != null) {
                        (kplan.eventBus as KPLocalEventBus).register(iid) {
                            if (it is KPEventBus.Event.State)
                                passed = it.state.info.taskName
                        }

                        start()

                        passed shouldMatch equalTo("A" as String?)
                    }
                }

                run {
                    var passed: String? = null
                    val iid = instanceId
                    if (iid != null) {
                        (kplan.eventBus as KPLocalEventBus).register(iid) {
                            if (it is KPEventBus.Event.State)
                                passed = it.state.info.taskName
                        }

                        val instance = kplan.getInstance(iid, RunningState.ONLY_RUNNING)!!
                        kplan.compute(instance)

                        passed shouldMatch equalTo("A" as String?)
                    }
                }

            }
        }
    }

    given("a complex workflow") {

        group("on reboot") {

            it("should show the first state only") {

                val kplan = helper.newKplan()
                var instanceId: InstanceId? = null

                run {
                    val (instance, start) = kplan.create(workflow = getComplextWorkflow(kplan), input = Instance.Input(0, "", Unit))

                    instanceId = instance.info.id

                    var passed: Set<String> = HashSet()
                    val iid = instanceId
                    if (iid != null) {
                        (kplan.eventBus as KPLocalEventBus).register(iid) {
                            if (it is KPEventBus.Event.State)
                                passed += it.state.info.taskName
                        }

                        start()

                        passed shouldMatch equalTo(hashSetOf("A", "!${genericParallelName}", "B", "C", "D", "E") as Set<String>)
                    }
                }

                run {
                    var passed: Set<String> = HashSet()
                    val iid = instanceId
                    if (iid != null) {
                        (kplan.eventBus as KPLocalEventBus).register(iid) {
                            if (it is KPEventBus.Event.State)
                                passed += it.state.info.taskName
                        }

                        kplan.computeAll()

                        passed shouldMatch equalTo(hashSetOf("B", "C", "E") as Set<String>)
                    }
                }
            }
        }
    }

})