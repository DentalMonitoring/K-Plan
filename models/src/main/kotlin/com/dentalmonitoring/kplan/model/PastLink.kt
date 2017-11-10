package com.dentalmonitoring.kplan.model

data class PastLink(
        val info: Info,
        val output: Any
) {
    constructor(pastStateId: StateId, nextStateId: StateId, instanceId: InstanceId, linkType: Link.Type, linkName: String, output: Any)
    : this(Info(Id(pastStateId, nextStateId, instanceId), Link.Id(linkType, linkName)), output)

    data class Info(
            val id: Id,
            val linkId: Link.Id
    )

    data class Id(
        val pastStateId: StateId,
        val nextStateId: StateId,
        val instanceId: InstanceId
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PastLink) return false

        return (info == other.info)
    }

    override fun hashCode(): Int {
        return info.hashCode()
    }

}
