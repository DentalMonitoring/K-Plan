package com.dentalmonitoring.kplan.workflow

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.workflow.task.*
import com.dentalmonitoring.kplan.workflow.task.utils.Inputs
import java.util.*

class Workflow<WI : Any, WO : Any> private constructor(val name: String) {

    internal val tasks: MutableMap<String, Task<Any, *, WI>> = HashMap()

    internal fun register(task: Task<Any, *, WI>) {
        if (task.name in tasks)
            throw IllegalStateException("The task ${task.name} is already defined")
        tasks[task.name] = task
    }

    internal var startTask: Task<Unit, *, WI>? = null
        set(task) {
            task ?: throw IllegalStateException("Cannot set a null workflow start task")
            if (task.parent != this)
                throw IllegalStateException("Workflow start task must be a task of this workflow")
            field = task
        }

    private fun _ready(task: Task<*, *, WI>, fromTask: String?, previousTasks: List<String>) {
        if (task.name in previousTasks)
            return
        task.linkedFrom(fromTask, previousTasks)
        val newPrevious = previousTasks + task.name
        task.links.forEach { _ready(it.second, task.name, newPrevious) }
    }

    internal fun ready() {
        _ready(startTask ?: throw IllegalStateException("Start task of workflow $name is not defined"), null, emptyList())
    }

    class Builder<WI : Any, WO : Any>(name: String) {
        internal val _workflow = Workflow<WI, WO>(name)

        fun <TI : Any, TO : Any> _businessTask(name: String, inCls: Class<TI>, compute: ((BusinessTask<TI, TO, WI>.Business) -> Unit)? = null) = BusinessTask(_workflow, name, inCls, compute)
        inline fun <reified TI : Any, TO : Any> businessTask(name: String, noinline compute: ((BusinessTask<TI, TO, WI>.Business) -> Unit)? = null) = _businessTask(name, TI::class.java, compute)

        fun <TI : Any, TO : Any, SWI : Any> _workflowTask(name: String, workflow: Workflow<SWI, TO>, inCls: Class<TI>, transform: (Inputs<TI, WI>) -> Instance.Input<SWI>) = WorkflowTask(_workflow, name, workflow, inCls, transform)
        inline fun <reified TI : Any, TO : Any, SWI : Any> workflowTask(name: String, workflow: Workflow<SWI, TO>, noinline transform: (Inputs<TI, WI>) -> Instance.Input<SWI>) = _workflowTask(name, workflow, TI::class.java, transform)
        fun <TO : Any> workflowTaskFromInstance(name: String, workflow: Workflow<WI, TO>) = _workflowTask(name, workflow, Unit::class.java) { it.instance }
        inline fun <reified TI : Any, TO : Any> workflowTaskFromState(name: String, workflow: Workflow<TI, TO>) = _workflowTask(name, workflow, TI::class.java) { Instance.Input(0, "", it.state) }

        fun <TI : Any, WI : Any> fromState() = { i: Inputs<TI, WI> -> Instance.Input(0, "", i.state) }
        fun <TI : Any, WI : Any> fromInstance() = { i: Inputs<TI, WI> -> i.instance }

        fun <TI : Any, TO : Any> _proxyTask(name: String, inCls: Class<TI>, transform: (Inputs<TI, WI>) -> TO) = ProxyTask(_workflow, name, inCls, transform)
        inline fun <reified TI : Any, TO : Any> proxyTask(name: String, noinline transform: (Inputs<TI, WI>) -> TO) = _proxyTask(name, TI::class.java, transform)

        fun <TI : Any> flowBranchTask(name: String, branch: (Inputs<TI, WI>) -> String) = FlowBranchTask(_workflow, name, branch)

        fun <TI : Any> _endTask(name: String, isSpecial: Boolean, inCls: Class<TI>) = EndTask(_workflow, name, isSpecial, inCls)
        inline fun <reified TI : WO> normalEndTask(name: String) = _endTask(name, false, TI::class.java)
        inline fun <reified TI : Any> specialEndTask(name: String) = _endTask(name, true, TI::class.java)

        fun <TI : Any> parallelGate(name: String, vararg tasks: Task<TI, *, WI>) = ParallelGate(_workflow, name, *tasks)

        fun barrierGate(name: String) = BarrierGate(_workflow, name)

        fun <TI : Any> parallel(name: String, vararg paths: Path<TI, *, WI>): Path<TI, Map<String, Any>, WI> {
            val starts = paths.map { it.start }.toMutableList().toTypedArray()
            val start = parallelGate("!$name", *starts)
            val barrier = barrierGate("/$name")
            @Suppress("CAST_NEVER_SUCCEEDS")
            paths.forEach {
                (it.end as Task<*, Any, Any>).next = barrier as Task<Any, *, Any>
            }
            return TaskPath(start, barrier)
        }

        fun <TI : Any> sparallel(name: String, vararg paths: Path<TI, *, WI>) = parallel(name, *paths) then ignoreInput<Map<String, Any>>()

        private var _count = 0
        fun _genName(base: String) = "__$base-${_count++}"
        inline fun <reified TI : Any> ignoreInput() = proxyTask<TI, Unit>(_genName("ignore")) { Unit }
        inline fun <reified TI : Any> firstInput() = proxyTask<Map<String, Any>, TI>(_genName("firstInput")) { it.state.values.first { it is TI } as TI }

        fun flow(name: String) = Link(Link.Id(Link.Type.FLOW, name))
        fun signal(name: String) = Link(Link.Id(Link.Type.SIGNAL, name))
        fun interruption(name: String) = Link(Link.Id(Link.Type.INTERRUPTION, name))

        inner class OnLink<in TI : Any>(val from: Path<TI, *, WI>, val link: Link) {
            infix fun <TO : Any> then(to: Path<*, TO, WI>): Path<TI, TO, WI> = from.path(link, to)
        }

        infix fun <TI : Any> Path<TI, *, WI>.on(link: Link) = OnLink(this, link)

        infix fun <TI : Any, M : Any, TO : Any> Path<TI, M, WI>.then(path: Path<M, TO, WI>) = this.path(path)

        inner class _Start {
            infix fun <TO : Any, P : Path<Unit, TO, WI>> with(to: P): P {
                _workflow.startTask = to.start; return to
            }
        }

        val start = _Start()
    }
}
