package com.dentalmonitoring.kplan.model

import java.util.*

data class State<out I : Any>(
        val info: Info,
        val input: I
) {
    data class Info(
        val id: StateId,
        val instanceId: InstanceId,
        val rootInstanceId: InstanceId,
        val taskName: String,
        val startDate: Date = Date(),
        var endDate: Date? = null,
        val isEnd: Boolean = false
    ) {
        val isPast get() = endDate != null


        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Info) return false

            return (id == other.id)
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is State<*>) return false

        return (info == other.info)
    }

    override fun hashCode(): Int {
        return info.hashCode()
    }

}
