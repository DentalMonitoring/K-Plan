package com.dentalmonitoring.kplan.workflow.task

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEngine
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.workflow.Task
import com.dentalmonitoring.kplan.workflow.Workflow

class EndTask<in TI : Any, WI : Any>(parent: Workflow<WI, *>, name: String, internal val isSpecial: Boolean, inCls: Class<TI>) : Task<TI, Nothing, WI>(parent, name, inCls) {

    override val links: List<Pair<Link?, Task<*, *, WI>>> get() = emptyList()

    override fun prepare(): Type = if (isSpecial) Type.END_SPECIAL else Type.END

    override fun compute(engine: KPEngine, instance: Instance<WI>, state: State<TI>, running: List<State.Info>, first: Boolean) {
        // Nothing to do on an end task.
    }

}
