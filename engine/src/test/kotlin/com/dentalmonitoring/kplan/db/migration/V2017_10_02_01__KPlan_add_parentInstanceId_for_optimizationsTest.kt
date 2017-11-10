package com.dentalmonitoring.kplan.db.migration

import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.model.StateId
import com.dentalmonitoring.kplan.test.utils.DBMigrationSpecsHelper
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

@Suppress("UNUSED_VARIABLE")
class V2017_10_02_01__KPlan_add_parentInstanceId_for_optimizationsTest : Spek({
    val ROOT_INSTANCE_ID = "instance"
    val SUB_INSTANCE = "sub_instance"
    val SUB_SUB_INSTANCE = "sub_sub_instance"
    val STATE_1 = "state_1"
    val STATE_2 = "state_2"
    val SUB_STATE_1 = "sub_state_1"
    val SUB_STATE_2 = "sub_state_2"
    val SUB_SUB_STATE_1 = "sub_sub_state_1"
    val SUB_SUB_STATE_2 = "sub_sub_state_2"

    val helper = DBMigrationSpecsHelper()

    group("Given a database with all previous migrations applied") {
        beforeGroup {
            helper.cleanDB()
            helper.applyMigration {
                V2016_08_03_01__KPlan_initialization.migrate(it)
                V2017_07_12_01__KPlan_add_constraint.migrate(it)
            }
        }

        given("a 3 layers workflow instanciated as described below") {
            //                             link
            // instance         : state_1 ------> state_2       sub_link
            // sub_instance     :                [ sub_state_1 ---------> sub_state_2 ]   sub_sub_link
            // sub_sub_instance :                                      [ sub_sub_state_1 -------------> sub_sub_state_2 ]

            beforeGroup {
                helper.executeSQLStatement("""
                    INSERT INTO k_instance VALUES ('$ROOT_INSTANCE_ID', '$ROOT_INSTANCE_ID', 'workflow-1', '$STATE_1', null, '2017-01-01', null, 1, 'input-str', 'kotlin.Unit', '{}');
                    INSERT INTO k_instance VALUES ('$SUB_INSTANCE', '$ROOT_INSTANCE_ID', 'sub-workflow-1', '$SUB_STATE_1', '$STATE_1', '2017-01-01', null, 1, 'input-str', 'kotlin.Unit', '{}');
                    INSERT INTO k_instance VALUES ('$SUB_SUB_INSTANCE', '$ROOT_INSTANCE_ID', 'sub-workflow-1', '$SUB_SUB_STATE_1', '$SUB_STATE_2', '2017-01-01', '2017-01-01', 1, 'input-str', 'kotlin.Unit', '{}');

                    INSERT INTO k_state VALUES ('$STATE_1', '$ROOT_INSTANCE_ID', '$ROOT_INSTANCE_ID', 'task-1', '2017-01-01', '2017-01-01', 'kotlin.Unit', '{}', 0);
                    INSERT INTO k_state VALUES ('$STATE_2', '$ROOT_INSTANCE_ID', '$ROOT_INSTANCE_ID', 'task-2', '2017-01-01', '2017-01-01', 'kotlin.Unit', '{}', 0);
                    INSERT INTO k_state VALUES ('$SUB_STATE_1', '$SUB_INSTANCE', '$ROOT_INSTANCE_ID', 'sub-task-1', '2017-01-01', '2017-01-01', 'kotlin.Unit', '{}', 0);
                    INSERT INTO k_state VALUES ('$SUB_STATE_2', '$SUB_INSTANCE', '$ROOT_INSTANCE_ID', 'sub-task-2', '2017-01-01', '2017-01-01', 'kotlin.Unit', '{}', 0);
                    INSERT INTO k_state VALUES ('$SUB_SUB_STATE_1', '$SUB_SUB_INSTANCE', '$ROOT_INSTANCE_ID', 'sub-sub-task-1', '2017-01-01', '2017-01-01', 'kotlin.Unit', '{}', 0);
                    INSERT INTO k_state VALUES ('$SUB_SUB_STATE_2', '$SUB_SUB_INSTANCE', '$ROOT_INSTANCE_ID', 'sub-sub-task-2', '2017-01-01', null, 'kotlin.Unit', '{}', 0);

                    INSERT INTO k_link VALUES ('$STATE_1', '$STATE_2', 'FLOW', '', 'kotlin.Unit', '{}');
                    INSERT INTO k_link VALUES ('$SUB_STATE_1', '$SUB_STATE_2', 'FLOW', '', 'kotlin.Unit', '{}');
                    INSERT INTO k_link VALUES ('$SUB_SUB_STATE_1', '$SUB_SUB_STATE_2', 'FLOW', '', 'kotlin.Unit', '{}');
                    """)
            }

            on("doing the migration") {
                helper.applyMigration {
                    V2017_10_02_01__KPlan_add_parentInstanceId_for_optimizations.migrate(it)
                }

                it("should fill the new column i_parent_instance_id from k_instance with the parent instance id value") {

                    helper.persistence().getInstance(InstanceId(ROOT_INSTANCE_ID))?.info?.parentInstanceId shouldMatch absent()
                    helper.persistence().getInstance(InstanceId(SUB_INSTANCE))?.info?.parentInstanceId?.id shouldMatch equalTo(ROOT_INSTANCE_ID)
                    helper.persistence().getInstance(InstanceId(SUB_SUB_INSTANCE))?.info?.parentInstanceId?.id shouldMatch equalTo(SUB_INSTANCE)
                }

                it("should fill the new column l_root_instance_id from k_link with the root instance id value") {
                    val state1 = helper.persistence().getStateInfo(StateId(STATE_1))
                    val subState1 = helper.persistence().getStateInfo(StateId(SUB_STATE_1))
                    val subSubState1 = helper.persistence().getStateInfo(StateId(SUB_SUB_STATE_1))

                    helper.persistence().getLinksTo(StateId(STATE_2))[state1]?.id?.instanceId?.id shouldMatch equalTo(ROOT_INSTANCE_ID)
                    helper.persistence().getLinksTo(StateId(SUB_STATE_2))[subState1]?.id?.instanceId?.id shouldMatch equalTo(SUB_INSTANCE)
                    helper.persistence().getLinksTo(StateId(SUB_SUB_STATE_2))[subSubState1]?.id?.instanceId?.id shouldMatch equalTo(SUB_SUB_INSTANCE)
                }
            }

        }
    }
})