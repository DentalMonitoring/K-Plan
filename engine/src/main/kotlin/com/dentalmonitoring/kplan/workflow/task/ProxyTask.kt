package com.dentalmonitoring.kplan.workflow.task

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEngine
import com.dentalmonitoring.kplan.workflow.Task
import com.dentalmonitoring.kplan.workflow.Workflow
import com.dentalmonitoring.kplan.workflow.task.utils.Inputs

class ProxyTask<in TI : Any, TO : Any, WI : Any>(parent: Workflow<WI, *>, name: String, inCls: Class<TI>, val transform: (Inputs<TI, WI>) -> TO) : Task.Direct<TI, TO, WI>(parent, name, inCls) {

    override fun linkedFrom(from: String?, previous: List<String>) {
        if (next == null)
            throw IllegalStateException("ProxyTask $name must be connected to a task (in ${parent.name})")
    }

    override fun compute(engine: KPEngine, instance: Instance<WI>, state: State<TI>, running: List<State.Info>, first: Boolean) {
        move(engine, state.info, transform(Inputs(state.input, instance.input)))
    }
}
