package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.test.utils.*
import com.dentalmonitoring.kplan.workflow.KPEventBus
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.should.shouldMatch
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.util.*

@Suppress("UNUSED_VARIABLE")
class RetrySpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    given("a simple manual linear workflow") {

        group("on simple retry") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleManualLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))

                val run = {
                    start()
                    moveWithString("A", kplan, instance)

                    val b = kplan.getRunningBusinessStates(instance.info.id).first()
                    kplan.retryRunningState(b.info.id)
                }

                Triple(kplan, instance, run)
            }

            it("should show the current running state") {
                val (kplan, instance, run) = setup()
                run()

                val list = kplan.getRunningStates(instance.info.id)
                list shouldMatch oneElement(
                        hasTask("B")
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
                    cast<KPEventBus.Event.State.Running.Start>("Running.Start",
                        has(KPEventBus.Event.State::stateTaskName, equalTo("B"))
                    )
                )
            }
        }

        group("on retry a past state") {

            fun setup() = run {
                val kplan = helper.newKplan()
                val (instance, start) = kplan.create(workflow = getSimpleManualLinearWorkflow(kplan), input = Instance.Input(0, "", Unit))

                val run: () -> Unit = {
                    start()
                    val a = kplan.getRunningBusinessStates(instance.info.id).first()
                    kplan.getMove(a.info).flow("one")
                    val b = kplan.getRunningBusinessStates(instance.info.id).first()
                    kplan.retryRunningState(a.info.id)
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
