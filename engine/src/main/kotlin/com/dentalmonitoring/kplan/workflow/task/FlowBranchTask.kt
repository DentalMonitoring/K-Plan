package com.dentalmonitoring.kplan.workflow.task

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEngine
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.workflow.Task
import com.dentalmonitoring.kplan.workflow.Workflow
import com.dentalmonitoring.kplan.workflow.task.utils.Inputs

class FlowBranchTask<in TI : Any, WI : Any>(parent: Workflow<WI, *>, name: String, val branch: (Inputs<TI, WI>) -> String) : Task.Linkable<TI, Nothing, WI>(parent, name, null) {

    override fun linkedFrom(from: String?, previous: List<String>) {
        if (next != null)
            throw IllegalStateException("FlowBranchTask $name must only be connected with named flows (in ${parent.name})")
        if (links.isEmpty())
            throw IllegalStateException("FlowBranchTask $name must be connected to a task (in ${parent.name})")
    }

    override fun compute(engine: KPEngine, instance: Instance<WI>, state: State<TI>, running: List<State.Info>, first: Boolean) {
        move(engine, state.info, Link.Id(Link.Type.FLOW, branch(Inputs(state.input, instance.input))), state.input)
    }
}
