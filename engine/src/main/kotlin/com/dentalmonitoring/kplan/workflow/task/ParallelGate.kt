package com.dentalmonitoring.kplan.workflow.task

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEngine
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.workflow.Task
import com.dentalmonitoring.kplan.workflow.Workflow

class ParallelGate<TI : Any, WI : Any>(parent: Workflow<WI, *>, name: String, vararg val tasks: Task<TI, *, WI>) : Task<TI, TI, WI>(parent, name, null) {

    override val links: List<Pair<Link?, Task<*, *, WI>>> get() = tasks.map { null to it }

    override fun compute(engine: KPEngine, instance: Instance<WI>, state: State<TI>, running: List<State.Info>, first: Boolean) {
        engine.move(state.info, Link(Link.Id(Link.Type.FLOW, "")), state.input, listOf(*tasks))
    }

}
