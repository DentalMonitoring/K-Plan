package com.dentalmonitoring.kplan.model

import java.util.*

data class Instance<out I : Any>(
        val info: Info,
        val input: Input<I>
) {
    data class Info(
        val id: InstanceId,
        val rootInstanceId: InstanceId,
        val workflowName: String,
        val startStateId: StateId,
        val parentInstanceId: InstanceId? = null,
        val parentStateId: StateId? = null,
        val startDate: Date = Date(),
        var endDate: Date? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Info) return false

            return (id == other.id)
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    data class Input<out T : Any>(
        val int: Int,
        val str: String,
        val obj: T
    )

    data class RunningState(val taskName: String, val stateId: String)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Instance<*>) return false

        return (info == other.info)
    }

    override fun hashCode(): Int {
        return info.hashCode()
    }
}
