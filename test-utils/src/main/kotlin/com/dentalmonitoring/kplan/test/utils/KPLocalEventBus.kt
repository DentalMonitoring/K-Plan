package com.dentalmonitoring.kplan.test.utils

import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.model.StateId
import com.dentalmonitoring.kplan.workflow.KPEventBus
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class KPLocalEventBus : KPEventBus {

    class Registration(
        val instanceId: InstanceId,
        val stateId: StateId? = null,
        val cb: (KPEventBus.Event) -> Unit
    )

    // TODO: really a string as key ?
    val _regs = ConcurrentHashMap<String, Registration>()

    fun register(reg: Registration): String {
        val id = UUID.randomUUID().toString()
        _regs[id] = reg
        return id
    }

    fun register(instanceId: InstanceId, stateId: StateId? = null, cb: (KPEventBus.Event) -> Unit) = register(Registration(instanceId, stateId, cb))

    fun unregister(registrationId: String) {
        _regs.remove(registrationId)
    }

    override fun fire(event: KPEventBus.Event) {

        for (reg in _regs.values)
            if (event.instances.any { it.info.id == reg.instanceId }
                && (event !is KPEventBus.Event.State
                    || (reg.stateId == null
                        || reg.stateId == event.state.info.id
                       )
                   )
                ) {
                reg.cb(event)
            }
    }
}
