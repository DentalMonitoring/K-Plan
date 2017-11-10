package com.dentalmonitoring.kplan.db.migration

import java.sql.Connection

class V2017_10_02_01__KPlan_add_parentInstanceId_for_optimizations {
    companion object {
        fun migrate(connection: Connection) {
            addParentInstanceIdForInstances(connection)
            addParentInstanceIdForLinks(connection)
        }

        private fun addParentInstanceIdForInstances(connection: Connection) {
            connection.prepareStatement("ALTER TABLE k_instance ADD COLUMN i_parent_instance_id varchar(255) NULL AFTER i_start_state_id").use { it.execute() }
            connection.prepareStatement("ALTER TABLE k_instance ADD CONSTRAINT i_parent_instance_id FOREIGN KEY (i_parent_instance_id) REFERENCES k_instance(i_id) ON DELETE CASCADE ON UPDATE CASCADE").use { it.execute() }

            connection.prepareStatement("""
                UPDATE k_instance I
                SET I.i_parent_instance_id = (
                  SELECT s_instance_id
                  FROM k_state S
                  WHERE I.i_parent_state_id = S.s_id AND I.i_parent_state_id IS NOT NULL
                )
            """).execute()
        }

        private fun addParentInstanceIdForLinks(connection: Connection) {
            connection.prepareStatement("ALTER TABLE k_link ADD COLUMN l_instance_id varchar(255) NOT NULL AFTER l_next_state_id").use { it.execute() }

            connection.prepareStatement("""
                UPDATE k_link L
                SET L.l_instance_id = (
                  SELECT s_instance_id
                  FROM k_state S
                  WHERE L.l_past_state_id = S.s_id
                )
            """).execute()

            connection.prepareStatement("ALTER TABLE k_link ADD CONSTRAINT l_instance_id FOREIGN KEY (l_instance_id) REFERENCES k_instance(i_id) ON DELETE CASCADE ON UPDATE CASCADE").use { it.execute() }
        }
    }
}
