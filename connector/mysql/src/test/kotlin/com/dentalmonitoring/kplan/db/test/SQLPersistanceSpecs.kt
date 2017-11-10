package com.dentalmonitoring.kplan.db.test

import com.dentalmonitoring.kplan.db.InstanceSQL
import com.dentalmonitoring.kplan.db.PastLinkSQL
import com.dentalmonitoring.kplan.db.StateSQL
import com.dentalmonitoring.kplan.model.*
import com.dentalmonitoring.kplan.test.utils.*
import com.dentalmonitoring.kplan.workflow.KPPersistence
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.should.shouldMatch
import org.h2.engine.User
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.util.*

class SQLPersistanceSpecs : Spek({

    val helper = KPlanSpecsHelper(this)

    val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }.time
    val today = Date()

    val MAIN_INSTANCE_ID = InstanceId("main-instance-id")
    val MAIN_WORKFLOW_NAME = "main-workflow-name"
    val MAIN_FIRST_STATE_ID = StateId("main-first-state-id")
    val MAIN_FIRST_TASK_NAME = "main-first-task-name"
    val MAIN_SECOND_STATE_ID = StateId("main-second-state-id")
    val MAIN_SECOND_TASK_NAME = "main-second-task-name"

    val mainInstanceInput = Instance.Input(42, "string", User("Salomon", 29))
    val mainInstance = Instance(Instance.Info(MAIN_INSTANCE_ID, MAIN_INSTANCE_ID, MAIN_WORKFLOW_NAME, MAIN_FIRST_STATE_ID, null, null, yesterday), mainInstanceInput)
    val mainPastInstance = Instance(Instance.Info(MAIN_INSTANCE_ID, MAIN_INSTANCE_ID, MAIN_WORKFLOW_NAME, MAIN_FIRST_STATE_ID, null, null, yesterday, today), mainInstanceInput)
    val mainFirstState = State(State.Info(MAIN_FIRST_STATE_ID, MAIN_INSTANCE_ID, MAIN_INSTANCE_ID, MAIN_FIRST_TASK_NAME, yesterday), Unit)
    val mainFirstPastState = State(State.Info(MAIN_FIRST_STATE_ID, MAIN_INSTANCE_ID, MAIN_INSTANCE_ID, MAIN_FIRST_TASK_NAME, yesterday, today), Unit)
    val mainSecondState = State(State.Info(MAIN_SECOND_STATE_ID, MAIN_INSTANCE_ID, MAIN_INSTANCE_ID, MAIN_SECOND_TASK_NAME, yesterday), "input")
    val mainFirstPastLink = PastLink(PastLink.Info(PastLink.Id(MAIN_FIRST_STATE_ID, MAIN_SECOND_STATE_ID, MAIN_INSTANCE_ID), Link.Id(Link.Type.FLOW, "")), "input")

    given("an instance and a state") {

        group("on creation") {

            it("should have created an instance in the DB") {
                helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }

                helper.db.connection.use {
                    with(InstanceSQL) {
                        val result = it.createStatement().executeQuery("SELECT * FROM `$Table`")

                        result.next() shouldMatch isTrue

                        result shouldMatch (
                            has(stringField(FId), equalTo(MAIN_INSTANCE_ID.id))
                                and has(stringField(FWorkflowName), equalTo(MAIN_WORKFLOW_NAME))
                                and has(intField(FInputInt), equalTo(42))
                                and has(stringField(FInputStr), equalTo("string"))
                                and has(stringField(FInputObjType), equalTo("com.dentalmonitoring.kplan.test.utils.User"))
                                and has(stringField(FInputObj), equalTo("{\n  \"name\": \"Salomon\",\n  \"age\": 29\n}"))
                                and has(stringField(FStartStateId), equalTo(MAIN_FIRST_STATE_ID.id))
                                and has(stringField(FParentStateId), absent())
                                and has(timestampField(FStartDate), timeEqualTo(yesterday))
                                and has(timestampField(FEndDate), absent()))

                        result.next() shouldMatch isFalse
                    }
                }
            }

            it("should have created a state in the DB") {
                helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }

                helper.db.connection.use {
                    with(StateSQL) {
                        val result = it.createStatement().executeQuery("SELECT * FROM `$Table`")

                        result.next() shouldMatch isTrue

                        result shouldMatch (
                            has(stringField(FId), equalTo(MAIN_FIRST_STATE_ID.id))
                                and has(stringField(FInstanceId), equalTo(MAIN_INSTANCE_ID.id))
                                and has(stringField(FTaskName), equalTo(MAIN_FIRST_TASK_NAME))
                                and has(stringField(FInputType), equalTo("kotlin.Unit"))
                                and has(stringField(FInput), equalTo("{}"))
                                and has(timestampField(FStartDate), timeEqualTo(yesterday))
                                and has(timestampField(FEndDate), absent()))
                    }
                }
            }
        }

        group("on locking") {

            itR("should throw on bad instance id") {
                val run: () -> Unit = { helper.newPersistance().lockRunningInstanceRootTree(InstanceId("bad-root-instance-id"), InstanceId("bad-instance-id")) }
                run shouldMatch throws<IllegalStateException>()
            }

            itR("should return a coherent result") {
                val pers = helper.newPersistance()
                val lock = pers.apply { putInstanceAndState(mainInstance, mainFirstState) }.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)
                lock?.transaction?.autoCleanup()

                lock shouldMatch present(
                    has(KPPersistence.LockInstanceResult::instancesHierarchy, oneElement(equalTo(mainInstance as Instance<*>)))
                        and has("running", { pers.getStatesInfoOf(mainInstance.info.id, running = RunningState.ONLY_RUNNING) }, oneElement(equalTo(mainFirstState.info)))
                )
            }

        }

        group("on querying") {

            it("should return null for a bad instance ID") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val instanceInfo = pers.getInstanceInfo(InstanceId("bad-instance-id"), running = RunningState.ONLY_RUNNING)

                instanceInfo shouldMatch absent()
            }

            it("should return the instance if it exists") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val instanceInfo = pers.getInstanceInfo(MAIN_INSTANCE_ID, running = RunningState.ONLY_RUNNING)

                instanceInfo shouldMatch present(equalTo(mainInstance.info))
            }

            it("should return null if the instance is past") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainPastInstance, mainFirstPastState) }
                val instanceInfo = pers.getInstanceInfo(MAIN_INSTANCE_ID, running = RunningState.ONLY_RUNNING)

                instanceInfo shouldMatch absent()
            }

            it("should return null for a bad state ID") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val state = pers.getStateInfo(StateId("bad-state-id"), running = RunningState.ONLY_RUNNING)

                state shouldMatch absent()
            }

            it("should return the state if it exists") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val stateInfo = pers.getStateInfo(MAIN_FIRST_STATE_ID, running = RunningState.ONLY_RUNNING)

                stateInfo shouldMatch present(equalTo(mainFirstState.info))
            }

            it("should return null if the state is past") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainPastInstance, mainFirstPastState) }
                val stateInfo = pers.getStateInfo(MAIN_FIRST_STATE_ID, running = RunningState.ONLY_RUNNING)

                stateInfo shouldMatch absent()
            }

        }

    }

    given("a transaction") {

        group("on putting states and links") {

            itR("should not appear before commit") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val lock = pers.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!

                lock.transaction.use {
                    it.putStatesAndLinks(listOf(mainSecondState), listOf(mainFirstPastLink))
                }

                val con = helper.db.connection.autoCleanup()

                with(StateSQL) {
                    val result = con.createStatement().executeQuery("SELECT * FROM `$Table`")

                    result.next() shouldMatch isTrue
                    result shouldMatch has(stringField(FId), equalTo(MAIN_FIRST_STATE_ID.id))

                    result.next() shouldMatch isFalse
                }

                with(PastLinkSQL) {
                    val result = con.createStatement().executeQuery("SELECT * FROM `$Table`")

                    result.next() shouldMatch isFalse
                }
            }

            itR("should appear after commit") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val lock = pers.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!

                lock.transaction.use {
                    it.putStatesAndLinks(listOf(mainSecondState), listOf(mainFirstPastLink))
                    it.commit()
                }

                val con = helper.db.connection.autoCleanup()

                with(StateSQL) {
                    val result = con.createStatement().executeQuery("SELECT * FROM `$Table`")

                    result.next() shouldMatch isTrue
                    result shouldMatch has(stringField(FId), equalTo(MAIN_FIRST_STATE_ID.id))

                    result.next() shouldMatch isTrue
                    result shouldMatch has(stringField(FId), equalTo(MAIN_SECOND_STATE_ID.id))

                    result.next() shouldMatch isFalse
                }

                with(PastLinkSQL) {
                    val result = con.createStatement().executeQuery("SELECT * FROM `$Table`")

                    result.next() shouldMatch isTrue
                    result shouldMatch (
                        has(stringField(FPastStateId), equalTo(MAIN_FIRST_STATE_ID.id))
                            and has(stringField(FNextStateId), equalTo(MAIN_SECOND_STATE_ID.id))
                            and has(stringField(FInstanceId), equalTo(MAIN_INSTANCE_ID.id))
                            and has(stringField(FLinkType), equalTo(Link.Type.FLOW.name))
                            and has(stringField(FLinkName), isEmptyString))

                    result.next() shouldMatch isFalse
                }

            }

        }

        group("on updating a state") {

            itR("should not appear before commit") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val lock = pers.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!

                lock.transaction.use {
                    it.updateStatesInfoDate(listOf(mainFirstPastState.info))
                }

                val con = helper.db.connection.autoCleanup()

                with(StateSQL) {
                    val result = con.createStatement().executeQuery("SELECT * FROM `$Table`")

                    result.next() shouldMatch isTrue
                    result shouldMatch (
                        has(stringField(FId), equalTo(MAIN_FIRST_STATE_ID.id))
                            and has(timestampField(FEndDate), absent()))

                    result.next() shouldMatch isFalse
                }
            }

            itR("should appear after commit") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val lock = pers.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!

                lock.transaction.use {
                    it.updateStatesInfoDate(listOf(mainFirstPastState.info))
                    it.commit()
                }

                val con = helper.db.connection.autoCleanup()

                with(StateSQL) {
                    val result = con.createStatement().executeQuery("SELECT * FROM `$Table`")

                    result.next() shouldMatch isTrue
                    result shouldMatch (
                        has(stringField(FId), equalTo(MAIN_FIRST_STATE_ID.id))
                            and has(timestampField(FEndDate), timeEqualTo(today)))

                    result.next() shouldMatch isFalse
                }

            }

        }

        group("on updating an instance") {

            itR("should not appear before commit") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val lock = pers.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!

                lock.transaction.use {
                    it.updateInstancesInfoDate(listOf(mainPastInstance.info))
                }

                val con = helper.db.connection.autoCleanup()

                with(InstanceSQL) {
                    val result = con.createStatement().executeQuery("SELECT * FROM `$Table`")

                    result.next() shouldMatch isTrue
                    result shouldMatch (
                        has(stringField(FId), equalTo(MAIN_INSTANCE_ID.id))
                            and has(timestampField(FEndDate), absent()))

                    result.next() shouldMatch isFalse
                }
            }

            itR("should appear after commit") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val lock = pers.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!

                lock.transaction.use {
                    it.updateInstancesInfoDate(listOf(mainPastInstance.info))
                    it.commit()
                }

                val con = helper.db.connection.autoCleanup()

                with(InstanceSQL) {
                    val result = con.createStatement().executeQuery("SELECT * FROM `$Table`")

                    result.next() shouldMatch isTrue
                    result shouldMatch (
                        has(stringField(FId), equalTo(MAIN_INSTANCE_ID.id))
                            and has(timestampField(FEndDate), timeEqualTo(today)))

                    result.next() shouldMatch isFalse
                }
            }
        }

        group("on asking for first running states") {

            itR("should return a state if it exists") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val lock = pers.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)
                lock?.transaction?.autoCleanup()

                lock shouldMatch present(anything)

                val stateInfo = pers.getStateInfoOf(MAIN_INSTANCE_ID, MAIN_FIRST_TASK_NAME, running = RunningState.ONLY_RUNNING)

                stateInfo shouldMatch present(has(State.Info::id, equalTo(MAIN_FIRST_STATE_ID)))
            }

            itR("should return null if there is none") {
                val pers = helper.newPersistance().apply { putInstanceAndState(mainInstance, mainFirstState) }
                val lock = pers.lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)
                lock?.transaction?.autoCleanup()

                lock shouldMatch present(anything)

                val state = pers.getStateInfoOf(MAIN_INSTANCE_ID, "unknown-task", running = RunningState.ONLY_RUNNING)

                state shouldMatch absent()
            }

        }

    }

    given("A state / instance tree") {

        val SUB_INSTANCE_ID = InstanceId("sub-instance-id")
        val SUB_WORKFLOW_NAME = "sub-workflow-name"
        val SUB_STATE_ID = StateId("sub-first-state-id")
        val SUB_TASK_NAME = "sub-first-task-name"

        val subInstanceInput = Instance.Input(0, "", Unit)
        val subInstance = Instance(Instance.Info(SUB_INSTANCE_ID, MAIN_INSTANCE_ID, SUB_WORKFLOW_NAME, SUB_STATE_ID, MAIN_INSTANCE_ID, MAIN_SECOND_STATE_ID, today), subInstanceInput)
        val subState = State(State.Info(SUB_STATE_ID, SUB_INSTANCE_ID, MAIN_INSTANCE_ID, SUB_TASK_NAME, today), Unit)

        fun KPPersistence.setUpTree() {
            putInstanceAndState(mainInstance, mainFirstState)

            lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!.transaction.use {
                it.updateStatesInfoDate(listOf(mainFirstPastState.info))
                it.putStatesAndLinks(listOf(mainSecondState), listOf(mainFirstPastLink))
                it.commit()
            }

            putInstanceAndState(subInstance, subState)
        }

        group("on getting links to a running task") {

            it("should return no links if the task does not exists") {
                val pers = helper.newPersistance().apply { setUpTree() }
                val links = pers.getLinksTo(StateId("unknown-state"))

                links.keys shouldMatch isEmpty
            }

            it("should return no links if the task is the start task of a workflow") {
                val pers = helper.newPersistance().apply { setUpTree() }
                val links = pers.getLinksTo(SUB_STATE_ID)

                links.keys shouldMatch isEmpty
            }

            it("should return the links to the task otherwise") {
                val pers = helper.newPersistance().apply { setUpTree() }
                val links = pers.getLinksTo(MAIN_SECOND_STATE_ID)

                links.entries shouldMatch oneElement(
                    has("key", Map.Entry<State.Info, PastLink.Info>::key, equalTo(mainFirstState.info))
                        and has("value", Map.Entry<State.Info, PastLink.Info>::value, equalTo(mainFirstPastLink.info)))
            }

        }

        group("on getting tree instance IDs") {

            it("should fail for an unknown task ID") {
                val run: () -> Unit = {
                    val pers = helper.newPersistance().apply { setUpTree() }
                    pers.getInstanceHierarchy(InstanceId("bad-instance-id"))
                }

                run shouldMatch throws<IllegalStateException>()
            }

            it("should return a list containing only the ID of a root instance") {
                val pers = helper.newPersistance().apply { setUpTree() }
                val list = pers.getInstanceHierarchy(MAIN_INSTANCE_ID)

                list shouldMatch oneElement(
                    equalTo(mainInstance as Instance<*>))
            }

            it("should return a list containing the ID of a sub instance and of its root") {
                val pers = helper.newPersistance().apply { setUpTree() }
                val list = pers.getInstanceHierarchy(SUB_INSTANCE_ID)

                list shouldMatch elements(
                    equalTo(subInstance as Instance<*>),
                    equalTo(mainInstance as Instance<*>))
            }

        }

    }

    group("Testing the getPreviousStates feature") {

        given("an instance and 4 states on 2 branches") {

            val MAIN_OTHER_BRANCH_STATE_ID = StateId("main-other-branch-state-id")
            val MAIN_OTHER_BRANCH_TASK_NAME = "main-other-branch-task-name"
            val MAIN_THIRD_STATE_ID = StateId("main-third-state-id")
            val MAIN_THIRD_TASK_NAME = "main-third-task-name"
            val mainThirdState = State(State.Info(MAIN_THIRD_STATE_ID, MAIN_INSTANCE_ID, MAIN_INSTANCE_ID, MAIN_THIRD_TASK_NAME, yesterday), "input")
            val mainSecondPastLink = PastLink(PastLink.Info(PastLink.Id(MAIN_SECOND_STATE_ID, MAIN_THIRD_STATE_ID, MAIN_INSTANCE_ID), Link.Id(Link.Type.FLOW, "")), "input")
            val mainOtherBranchState = State(State.Info(MAIN_OTHER_BRANCH_STATE_ID, MAIN_INSTANCE_ID, MAIN_INSTANCE_ID, MAIN_OTHER_BRANCH_TASK_NAME, yesterday, today), "input")
            val mainOtherBranchPastLink = PastLink(PastLink.Info(PastLink.Id(MAIN_OTHER_BRANCH_STATE_ID, MAIN_THIRD_STATE_ID, MAIN_INSTANCE_ID), Link.Id(Link.Type.FLOW, "")), "input")

            group("on asking for the state Id ancestors") {

                it("should retrieve all the ancestors") {
                    val pers = helper.newPersistance().apply {
                        putInstanceAndState(mainInstance, mainFirstState)
                        val lock = lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!
                        lock.transaction.use {
                            it.putStatesAndLinks(listOf(mainSecondState, mainOtherBranchState, mainThirdState), listOf(mainFirstPastLink, mainOtherBranchPastLink, mainSecondPastLink))
                            it.commit()
                        }
                    }

                    val ancestors = pers.getPreviousStates(mainThirdState.info)
                    ancestors.size shouldMatch equalTo(3) // hasSize(equalTo(3)) should work it isn't ???
                    ancestors shouldMatch equalTo(
                        hashSetOf(// How to do a `allElements` assert less ugly ?
                            mainOtherBranchState.info.id,
                            mainSecondState.info.id,
                            mainFirstState.info.id
                        ) as Set<StateId>
                    )
                }
            }
        }

        given("an instance and 3 states with only one ancestor") {

            val MAIN_THIRD_STATE_ID = StateId("main-third-state-id")
            val MAIN_THIRD_TASK_NAME = "main-third-task-name"
            val mainThirdState = State(State.Info(MAIN_THIRD_STATE_ID, MAIN_INSTANCE_ID, MAIN_INSTANCE_ID, MAIN_THIRD_TASK_NAME, yesterday), "input")
            val mainFirstToThirdStateLink = PastLink(PastLink.Info(PastLink.Id(MAIN_FIRST_STATE_ID, MAIN_THIRD_STATE_ID, MAIN_INSTANCE_ID), Link.Id(Link.Type.FLOW, "")), "input")

            group("on asking for the state Id ancestors") {

                it("should not retrieve the states that are not ancestors") {
                    val pers = helper.newPersistance().apply {
                        putInstanceAndState(mainInstance, mainFirstState)
                        val lock = lockRunningInstanceRootTree(InstanceId("NOT_IMPORTANT"), MAIN_INSTANCE_ID)!!
                        lock.transaction.use {
                            it.putStatesAndLinks(listOf(mainSecondState, mainThirdState), listOf(mainFirstPastLink, mainFirstToThirdStateLink))
                            it.commit()
                        }
                    }

                    val ancestors = pers.getPreviousStates(mainThirdState.info)
                    ancestors.size shouldMatch equalTo(1) // hasSize(equalTo(3)) should work it isn't ???
                    ancestors shouldMatch equalTo(
                        hashSetOf(// How to do a `allElements` assert less ugly ?
                            mainFirstState.info.id
                        ) as Set<StateId>
                    )
                }
            }
        }
    }
})
