package com.dentalmonitoring.kplan

import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPPersistence
import com.dentalmonitoring.kplan.model.Link
import com.dentalmonitoring.kplan.workflow.Task
import com.dentalmonitoring.kplan.workflow.Workflow
import com.dentalmonitoring.kplan.workflow.task.*
import java.util.*

internal fun Workflow<*, *>.generateGraphViz(db: KPPersistence, rootInstanceId: InstanceId?): String {
    if (rootInstanceId != null) {
        val states: List<State.Info> = db.getRecursiveStatesInfoOfRoot(rootInstanceId)
        val instances: List<Instance.Info> = db.getRootInstanceList(rootInstanceId)

        return GraphvizGenerator(PresenceChecker(states, instances)).generateGraphViz(this)
    }
    else {
        return GraphvizGenerator().generateGraphViz(this)
    }
}

enum class StatePresence { PAST, CURRENT }

// To help checking if a task has a past or current state
class PresenceChecker(val states: List<State.Info>, val instances: List<Instance.Info>) {

    fun getTaskStatePresence(task: Task<*, *, *>, parentName: String): StatePresence? {
        for (state in states) {
            if (state.taskName == task.name) {
                val instanceInfo = findInstanceByState(state)
                val parentTaskName = getParentTaskName(instanceInfo, "")

                if (instanceInfo.workflowName == task.parent.name && parentTaskName == parentName) {
                    return if (state.isPast) StatePresence.PAST else StatePresence.CURRENT
                }
            }
        }
        return null
    }

    private fun findInstanceByState(state: State.Info) = instances.single { it.id == state.instanceId }
    private fun findParentStateByInstance(instance: Instance.Info) = states.singleOrNull { it.id == instance.parentStateId }

    // generate the parent task name going through the instance tree, using this pattern :
    // grandParentTaskName:parentTaskName:taskName
    private tailrec fun getParentTaskName(instanceInfo: Instance.Info, resultName: String): String {
        val parentStateInfo = findParentStateByInstance(instanceInfo)

        if (parentStateInfo == null) return resultName
        else return getParentTaskName(findInstanceByState(parentStateInfo), ":" + parentStateInfo.taskName + resultName)
    }
}

class GraphvizGenerator(val presenceChecker: PresenceChecker? = null) {

    fun generateGraphViz(workflow: Workflow<*, *>): String {
        return generateGraphViz(workflow, "", "", 0)
    }

    private fun generateGraphViz(wf: Workflow<*, *>, parentName: String, wfName: String, depth: Int): String {
        return buildString {
            val graphType = if (parentName.isEmpty()) "digraph" else "subgraph"

            val indent = " ".repeat(depth * 4)

            val graphName = (if (depth == 0) "workflow" else "cluster$parentName")

            appendln(indent + "$graphType ${graphName.dotID} {")
            if (depth == 0) {
                appendln(indent + "    compound=true;")
                appendln(indent + "    rankdir=LR;")
                appendln(indent + "    nodesep=0.6;")
                appendln(indent + "    ranksep=0.8;")
                appendln(indent + "    pad=0.4;")
            }
            if (wfName.isNotEmpty())
                appendln(indent + "    label=${wfName.dotID};")

            val startName = (if (depth == 0) "start" else "$parentName-start")

            appendln(indent + "    ${startName.dotID} [shape=doublecircle,label=\"\"];")
            appendln(indent + "    ${link(parentName, startName, wf.startTask!!, null)};")

            for (task in wf.tasks.values) {
                if (task is ProxyTask)
                    continue
                else if (task is WorkflowTask<*, *, *, *>) {
                    val subWorkflowTaskName = "$parentName:${task.name}"
                    append(generateGraphViz(task.workflow, subWorkflowTaskName, task.name, depth + 1))
                    for ((link, linkedTask) in getOrderedLinks(task.links)) {
                        val endTask =
                            if (link == null)
                                task.workflow.tasks.values.first { it is EndTask && it.isSpecial.not() }
                            else if (link.id.type == Link.Type.FLOW)
                                task.workflow.tasks.values.find { it is EndTask && it.name == link.id.name }
                                ?: task.workflow.tasks.values.first { it is EndTask && it.isSpecial.not() }
                            else
                                task.workflow.startTask!!
                        appendln(indent + "    ${link(parentName, fullName(subWorkflowTaskName, endTask), linkedTask, link, "ltail" to "cluster$parentName:${task.name}".dotID)};")
                    }
                }
                else {
                    val props = when (task) {
                        is BusinessTask<*, *, *> -> hashMapOf("shape" to "box", "style" to "bold")
                        is FlowBranchTask<*, *> -> hashMapOf("shape" to "plaintext")
                        is ParallelGate<*, *> -> hashMapOf("shape" to "diamond")
                        is BarrierGate<*> -> hashMapOf("shape" to "Mdiamond")
                        is EndTask<*, *> -> if (task.isSpecial) hashMapOf("shape" to "ellipse", "style" to "filled", "bgcolor" to "lightgrey") else hashMapOf("shape" to "ellipse")
                        else -> throw IllegalStateException("Unknown task type ${task.javaClass.simpleName}")
                    }

                    when (presenceChecker?.getTaskStatePresence(task, parentName)) {
                        StatePresence.PAST -> {
                            props += hashMapOf("style" to "filled", "color" to "lightblue")
                        }
                        StatePresence.CURRENT -> {
                            props += hashMapOf("style" to "filled", "color" to "lightpink")
                        }
                    }

                    if (task !is ParallelGate<*, *> && task !is BarrierGate<*>)
                        props["label"] = task.name.dotID
                    else
                        props["label"] = "".dotID

                    val propStr = props.entries.map { "${it.key}=${it.value}" }.joinToString(",")
                    appendln(indent + "    ${fullName(parentName, task).dotID} [$propStr];")

                    for ((link, linkedTask) in getOrderedLinks(task.links)) {
                        appendln(indent + "    ${link(parentName, fullName(parentName, task), linkedTask, link)};")
                    }
                }
            }

            appendln(indent + "}")
        }
    }

    private fun fullName(name: String, task: Task<*, *, *>) = if (task is WorkflowTask<*, *, *, *>) "$name:${task.name}-start" else "$name@${task.name}"

    private val String.dotID: String get() = "\"" + replace("\"", "\\\"") + "\""

    private fun link(fromName: String, from: String, to: Task<*, *, *>, link: Link?, vararg props: Pair<String, String>): String {
        val propMap = HashMap<String, String>(mapOf(*props))

        val rTo = if (to is ProxyTask) to.next!! else to

        if (rTo is WorkflowTask<*, *, *, *>)
            propMap["lhead"] = "cluster$fromName:${rTo.name}".dotID

        when (link?.id?.type) {
            null -> {
                propMap["style"] = "bold"
            }
            Link.Type.FLOW -> {
            }
            Link.Type.SIGNAL -> {
                propMap["style"] = "dashed"; propMap["arrowhead"] = "veeodot"
            }
            Link.Type.INTERRUPTION -> {
                propMap["style"] = "dashed"; propMap["arrowhead"] = "veediamond"
            }
        }

        if (link != null)
            propMap["xlabel"] = link.id.name.dotID

        val propStr = propMap.entries.map { "${it.key}=${it.value}" }.joinToString(",")

        return "${from.dotID} -> ${fullName(fromName, rTo).dotID} [$propStr]"
    }

    private fun getOrderedLinks(links: List<Pair<Link?, Task<*, *, *>>>): List<Pair<Link?, Task<*, *, *>>> {
        return links.sortedByDescending {
            val first = it.first
            (first?.id?.name + first?.id?.type) + it.second.name
        }
    }
}