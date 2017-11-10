package com.dentalmonitoring.kplan.workflow

import com.dentalmonitoring.kplan.model.Link

interface Path<in TI : Any, TO : Any, WI : Any> {
    val start: Task<TI, *, WI>
    val end: Task<*, TO, WI>
}

data class TaskPath<in TI : Any, TO : Any, WI : Any>(
    override val start: Task<TI, *, WI>,
    override val end: Task<*, TO, WI>
) : Path<TI, TO, WI>


internal fun <TI : Any, M : Any, TO : Any, WI : Any> Path<TI, M, WI>.path(to: Path<M, TO, WI>): Path<TI, TO, WI> {
    this.end.next = to.start
    return TaskPath(start, to.end)
}

internal fun <TI : Any, TO : Any, WI : Any> Path<TI, *, WI>.path(link: Link, to: Path<*, TO, WI>): Path<TI, TO, WI> {
    this.end.addLink(link, to.start)
    return TaskPath(start, to.end)
}
