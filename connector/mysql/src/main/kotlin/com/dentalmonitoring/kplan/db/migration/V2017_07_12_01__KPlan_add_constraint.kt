package com.dentalmonitoring.kplan.db.migration

import java.sql.Connection

class V2017_07_12_01__KPlan_add_constraint {
    companion object {
        fun migrate(connection: Connection) {
            connection.prepareStatement("""
                ALTER TABLE k_state
                  ADD CONSTRAINT s_root_instance_id
                  FOREIGN KEY (s_root_instance_id) REFERENCES k_instance(i_id) ON DELETE CASCADE ON UPDATE CASCADE;
            """).use { it.execute() }
        }
    }
}