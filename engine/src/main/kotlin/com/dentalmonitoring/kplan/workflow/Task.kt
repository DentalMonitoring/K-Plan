package com.dentalmonitoring.kplan.workflow

import com.dentalmonitoring.kplan.KPlan
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.model.State
import java.util.*

abstract class Task<in TI : Any, TO : Any, WI : Any>(val parent: Workflow<WI, *>, val name: String, val inCls: Class<in TI>?) : Path<TI, TO, WI> {

    init {
        @Suppress("UNCHECKED_CAST")
        parent.register(this as Task<Any, *, WI>)
    }

    internal enum class Type { NORMAL, SINGLETON, END, END_SPECIAL }

    internal open fun prepare(): Type = Type.NORMAL

    internal open val isBusiness = false

    internal open val shouldRecompute = true

    internal open val hasSubWorkflow = false

    internal open fun getMove(engine: KPEngine, stateInfo: State.Info): KPlan.Move<TO> = throw IllegalStateException("${javaClass.simpleName} cannot be manually moved")

    internal open fun linkedFrom(from: String?, previous: List<String>) {}

    abstract internal fun compute(engine: KPEngine, instance: Instance<WI>, state: State<TI>, running: List<State.Info>, first: Boolean)


    override val start: Task<TI, TO, WI> get() = this

    override val end: Task<TI, TO, WI> get() = this


    internal open var next: Task<TO, *, WI>?
        get() = null
        set(n) = throw UnsupportedOperationException(javaClass.canonicalName)

    abstract val links: List<Pair<Link?, Task<*, *, WI>>>

    internal open fun <M : Any> addLink(link: Link, task: Task<M, *, WI>): Unit = throw UnsupportedOperationException()

    override fun toString(): String {
        return "${javaClass.simpleName}:$name"
    }

    abstract class Direct<in TI : Any, TO : Any, WI : Any>(parent: Workflow<WI, *>, name: String, inCls: Class<TI>?) : Task<TI, TO, WI>(parent, name, inCls) {

        override val links: List<Pair<Link?, Task<*, *, WI>>> get() = if (next != null) listOf(null to next!!) else emptyList()

        override var next: Task<TO, *, WI>? = null

        protected open fun findNext(): Pair<Link, List<Task<*, *, WI>>> {
            val nextTask = next
            return if (nextTask == null)
                Link(Link.Id(Link.Type.FLOW, "")) to emptyList()
            else
                Link(Link.Id(Link.Type.FLOW, "")) to listOf(nextTask)
        }

        internal fun move(engine: KPEngine, state: State.Info, output: Any) {
            val (link, nextTasks) = findNext()
            engine.move(state, link, output, nextTasks)
        }

    }

    abstract class Linkable<in TI : Any, TO : Any, WI : Any>(parent: Workflow<WI, *>, name: String, inCls: Class<TI>?) : Direct<TI, TO, WI>(parent, name, inCls) {

        private val _links = HashMap<Link, Task<*, *, WI>>()

        override val links: List<Pair<Link?, Task<*, *, WI>>> get() = _links.map { it.toPair() } + super.links

        override fun findNext(): Pair<Link, List<Task<*, *, WI>>> {
            val pair = super.findNext()
            if (pair.second.isNotEmpty())
                return pair

            if (_links.keys.any { it.id.type == Link.Type.FLOW })
                throw IllegalStateException("Default flow is not registered")

            return pair
        }

        internal fun findLink(linkId: Link.Id): Pair<Link, List<Task<*, *, WI>>> {
            if (linkId == Link.Id(Link.Type.FLOW, ""))
                return findNext()

            val (link, task) = _links.asSequence().firstOrNull { it.key.id == linkId } ?: throw IllegalStateException("Link $linkId is not registered")
            return link to listOf(task)
        }

        internal fun move(engine: KPEngine, stateInfo: State.Info, linkId: Link.Id, output: Any) {
            val (link, nextTasks) = findLink(linkId)
            engine.move(stateInfo, link, output, nextTasks)
        }

        override fun <M : Any> addLink(link: Link, task: Task<M, *, WI>) {
            if (this.parent != task.parent)
                throw IllegalStateException("You can only link two tasks of the same parent workflow")
            if (link in _links)
                throw IllegalStateException("Link $link already registered in ${task.name}")
            _links[link] = task
        }
    }

}
