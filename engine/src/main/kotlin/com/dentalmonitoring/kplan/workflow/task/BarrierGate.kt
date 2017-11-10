package com.dentalmonitoring.kplan.workflow.task

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEngine
import com.dentalmonitoring.kplan.workflow.Task
import com.dentalmonitoring.kplan.workflow.Workflow
import java.util.*

@Suppress("CAST_NEVER_SUCCEEDS")
class BarrierGate<WI : Any>(parent: Workflow<WI, *>, name: String) : Task.Direct<Any, Map<String, Any>, WI>(parent, name, null) {

    val previousTasks = HashSet<String>()

    override fun prepare(): Type = Type.SINGLETON

    override fun linkedFrom(from: String?, previous: List<String>) {
        if (previous.isEmpty())
            throw IllegalStateException("Cannot start with BarrierGate $name (in ${parent.name})")
        previousTasks += previous
        if (next == null)
            throw IllegalStateException("BarrierGate $name must be connected (in ${parent.name})")
    }


    override fun compute(engine: KPEngine, instance: Instance<WI>, state: State<Any>, running: List<State.Info>, first: Boolean) {
        val waiting = running.map { it.taskName }.intersect(previousTasks)
        if (waiting.isNotEmpty()) {
//            println("Waiting for $waiting")
            return
        }

//        println("Opening barrier")

        val links = engine.persistence.getLinksTo(state.info.id)
        val result = links.map { it.key.taskName to engine.persistence.getLinkOutput(it.value.id) }.filter { it.second != null }.toMap()
        move(engine, state.info, result)
    }
}
