package com.dentalmonitoring.kplan.db.migration

import java.sql.Connection

class V2016_08_03_01__KPlan_initialization {
    companion object {
        fun migrate(connection: Connection) {
            connection.prepareStatement("""
                CREATE TABLE `k_instance` (
                  `i_id` varchar(255) NOT NULL,
                  `i_root_instance_id` varchar(255) NOT NULL,
                  `i_workflow` varchar(255) NOT NULL,
                  `i_start_state_id` varchar(255) NOT NULL,
                  `i_parent_state_id` varchar(255) DEFAULT NULL,
                  `i_start_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `i_end_date` timestamp NULL DEFAULT NULL,
                  `i_input_int` int(11) NOT NULL,
                  `i_input_str` varchar(255) NOT NULL,
                  `i_input_obj_type` varchar(255) NOT NULL,
                  `i_input_obj` MEDIUMTEXT NOT NULL,
                  PRIMARY KEY (`i_id`),
                  KEY `i_workflow` (`i_workflow`),
                  KEY `i_parent_state_id` (`i_parent_state_id`),
                  KEY `i_start_date` (`i_start_date`),
                  KEY `i_end_date` (`i_end_date`),
                  KEY `i_input_int` (`i_input_int`),
                  KEY `i_input_str` (`i_input_str`),
                  CONSTRAINT `i_root_instance_id` FOREIGN KEY (`i_root_instance_id`) REFERENCES `k_instance` (`i_id`) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
            """).use { it.execute() }

            connection.prepareStatement("""
                CREATE TABLE `k_state` (
                  `s_id` varchar(255) NOT NULL,
                  `s_instance_id` varchar(255) NOT NULL,
                  `s_root_instance_id` varchar(255) NOT NULL,
                  `s_task` varchar(255) NOT NULL,
                  `s_start_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `s_end_date` timestamp NULL DEFAULT NULL,
                  `s_input_type` varchar(255) NOT NULL,
                  `s_input` text NOT NULL,
                  `s_is_end` boolean NULL DEFAULT 0,
                  PRIMARY KEY (`s_id`),
                  KEY `s_instance_id` (`s_instance_id`),
                  KEY `s_task` (`s_task`),
                  KEY `s_start_date` (`s_start_date`),
                  CONSTRAINT `s_instance_id` FOREIGN KEY (`s_instance_id`) REFERENCES `k_instance` (`i_id`) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
            """).use { it.execute() }

            connection.prepareStatement("""
                CREATE TABLE `k_link` (
                  `l_past_state_id` varchar(255) NOT NULL,
                  `l_next_state_id` varchar(255) NOT NULL,
                  `l_type` varchar(15) NOT NULL,
                  `l_name` varchar(255) NOT NULL,
                  `l_output_type` varchar(255) NOT NULL,
                  `l_output` text NOT NULL,
                  PRIMARY KEY (`l_past_state_id`, `l_next_state_id`),
                  KEY `l_past_state_id` (`l_past_state_id`),
                  KEY `l_next_state_id` (`l_next_state_id`),
                  CONSTRAINT `l_past_state_id` FOREIGN KEY (`l_past_state_id`) REFERENCES `k_state` (`s_id`) ON DELETE CASCADE ON UPDATE CASCADE,
                  CONSTRAINT `l_next_state_id` FOREIGN KEY (`l_next_state_id`) REFERENCES `k_state` (`s_id`) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
            """).use { it.execute() }
        }
    }
}