package com.dentalmonitoring.kplan.workflow

import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.model.StateId

interface KPEventBus {

    sealed class Event {
        abstract val instances: List<com.dentalmonitoring.kplan.model.Instance<*>>

        val instance: com.dentalmonitoring.kplan.model.Instance<*> get() = instances.first()

        val rootInstance: com.dentalmonitoring.kplan.model.Instance<*> get() = instances.last()

        sealed class Instance : Event() {
            sealed class Running : Instance() {
                data class Start(override val instances: List<com.dentalmonitoring.kplan.model.Instance<*>>) : Running()
                data class End(override val instances: List<com.dentalmonitoring.kplan.model.Instance<*>>, val linkType: Link.Type, val pastSaved: Boolean) : Running()
            }

            data class PastRemoved(override val instances: List<com.dentalmonitoring.kplan.model.Instance<*>>) : Instance()
        }

        sealed class State : Event() {
            abstract val state: com.dentalmonitoring.kplan.model.State<*>
            abstract val isBusiness: Boolean

            sealed class Running : State() {
                data class Start(override val instances: List<com.dentalmonitoring.kplan.model.Instance<*>>, override val state: com.dentalmonitoring.kplan.model.State<*>, override val isBusiness: Boolean, val previousStateIds: Set<StateId>? = null) : Running()
                data class End(override val instances: List<com.dentalmonitoring.kplan.model.Instance<*>>, override val state: com.dentalmonitoring.kplan.model.State<*>, override val isBusiness: Boolean, val linkType: Link.Type, val pastSaved: Boolean) : Running()
            }

            data class PastRemoved(override val instances: List<com.dentalmonitoring.kplan.model.Instance<*>>, override val state: com.dentalmonitoring.kplan.model.State<*>, override val isBusiness: Boolean) : State()
        }

    }

    fun fire(event: Event)

    companion object {
        operator fun invoke(fire: (event: Event) -> Unit) = object : KPEventBus {
            override fun fire(event: Event) = fire(event)
        }
    }
}
