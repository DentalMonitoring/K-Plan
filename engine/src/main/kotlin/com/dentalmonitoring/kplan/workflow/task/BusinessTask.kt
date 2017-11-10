package com.dentalmonitoring.kplan.workflow.task

import com.dentalmonitoring.kplan.KPlan
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEngine
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.workflow.Task
import com.dentalmonitoring.kplan.workflow.Workflow

@kotlin.Suppress("AddVarianceModifier")
class BusinessTask<TI : Any, TO : Any, WI : Any>(parent: Workflow<WI, *>, name: String, inCls: Class<TI>, val compute: ((BusinessTask<TI, TO, WI>.Business) -> Unit)?) : Task.Linkable<TI, TO, WI>(parent, name, inCls) {

    override val isBusiness = true

    override fun getMove(engine: KPEngine, stateInfo: State.Info): KPlan.Move<TO> {
        if (compute != null)
            throw IllegalStateException("Automatic business tasks $name cannot be manually advanced (in ${parent.name})")
        return Move(engine, stateInfo)
    }

    open inner class Move internal constructor(private val _engine: KPEngine, private val _stateInfo: State.Info) : KPlan.Move<TO> {

        override var isInterrupted = false

        private var _interruptCallback: () -> Unit = {}

        override fun flow(result: TO) = move(_engine, _stateInfo, result)

        override fun flow(name: String, result: Any) = move(_engine, _stateInfo, Link.Id(Link.Type.FLOW, name), result)

        override fun signal(name: String, result: Any) = move(_engine, _stateInfo, Link.Id(Link.Type.SIGNAL, name), result)

        override fun interrupt(name: String, result: Any) = move(_engine, _stateInfo, Link.Id(Link.Type.INTERRUPTION, name), result)

        override fun onInterrupt(callback: () -> Unit) {
            _interruptCallback = callback
        }
    }

    inner class Business internal constructor(engine: KPEngine, val instance: Instance<WI>, val state: State<TI>) : Move(engine, state.info)

    override fun compute(engine: KPEngine, instance: Instance<WI>, state: State<TI>, running: List<State.Info>, first: Boolean) {
        compute?.invoke(Business(engine, instance, state))
    }

}
