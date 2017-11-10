package com.dentalmonitoring.kplan.workflow.task

import com.dentalmonitoring.kplan.KPlan
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEngine
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.workflow.Task
import com.dentalmonitoring.kplan.workflow.Workflow
import com.dentalmonitoring.kplan.workflow.task.utils.Inputs

class WorkflowTask<in TI : Any, TO : Any, WI : Any, SWI : Any>(
    parent: Workflow<WI, *>,
    name: String,
    val workflow: Workflow<SWI, TO>,
    inCls: Class<TI>,
    val transform: (Inputs<TI, WI>) -> Instance.Input<SWI>
) : Task.Linkable<TI, TO, WI>(parent, name, inCls) {

    override val shouldRecompute = false

    override val hasSubWorkflow = true

    @Suppress("UNUSED_VARIABLE")
    override fun compute(engine: KPEngine, instance: Instance<WI>, state: State<TI>, running: List<State.Info>, first: Boolean) {
        if (first) {
            val (newInstance, start) = engine.create(workflow, transform(Inputs(state.input, instance.input)), state)
            start()
        }
        else {
            val subInstance = engine.persistence.getInstanceOf(state.info.id)
            if (subInstance != null)
                engine.compute(subInstance)
        }
    }

    override fun getMove(engine: KPEngine, stateInfo: State.Info): KPlan.Move<TO> {
        return Move(engine, stateInfo)
    }

    open inner class Move internal constructor(private val _engine: KPEngine, val stateInfo: State.Info) : KPlan.Move<TO> {

        override var isInterrupted = false

        override fun flow(result: TO) = throw IllegalStateException("Workflow task $name cannot be moved with flow (in ${parent.name})")

        override fun flow(name: String, result: Any) = throw IllegalStateException("Workflow task $name cannot be moved with flow (in ${parent.name})")

        override fun signal(name: String, result: Any) = move(_engine, stateInfo, Link.Id(Link.Type.SIGNAL, name), result)

        override fun interrupt(name: String, result: Any) = move(_engine, stateInfo, Link.Id(Link.Type.INTERRUPTION, name), result)

        override fun onInterrupt(callback: () -> Unit) = throw IllegalStateException("Workflow task $name cannot register interruption callbacks (in ${parent.name})")
    }
}
