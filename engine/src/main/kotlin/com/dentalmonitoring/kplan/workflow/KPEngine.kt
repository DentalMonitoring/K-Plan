package com.dentalmonitoring.kplan.workflow

import com.dentalmonitoring.kplan.KPlan
import com.dentalmonitoring.kplan.model.*
import com.dentalmonitoring.kplan.model.RunningState.ONLY_PAST
import com.dentalmonitoring.kplan.model.RunningState.ONLY_RUNNING
import com.dentalmonitoring.kplan.workflow.task.WorkflowTask
import java.util.*
import kotlin.collections.ArrayList

internal class KPEngine(internal val persistence: KPPersistence, internal val eventBus: KPEventBus) {

    internal val workflows = HashMap<String, Workflow<*, *>>()

    private fun Workflow<*, *>.getTaskOf(stateInfo: State.Info) = tasks[stateInfo.taskName] ?: throw IllegalStateException("State refers to unknown task ${stateInfo.taskName} in workflow $name")

    private fun getStateInput(stateId: StateId): Any = persistence.getStateInput(stateId) ?: throw IllegalArgumentException("Input not found for stateId=$stateId")

    private fun getWorkflow(workflowName: String): Workflow<*, *> = workflows[workflowName] ?: throw IllegalStateException("Instance refers to unknown workflow $workflowName")

    inner class TransactionHolder(
            private val _transaction: KPPersistence.Transaction,
            val instancesHierarchy: List<Instance<*>>,
            val onSuccess: MutableList<() -> Unit> = LinkedList()
    ) : KPPersistence.TransactionBase by _transaction {

        val instance: Instance<*> get() = instancesHierarchy[0]

        val running by lazy {
            persistence.getStatesInfoOf(instance.info.id, running = ONLY_RUNNING)
        }

        val workflow: Workflow<*, *> by lazy {
            workflows[instance.info.workflowName] ?: run {
                throw IllegalStateException("Instance refers to unknown workflow ${instance.info.workflowName}")
            }
        }

        fun subTransaction(subInstancesHierarchy: List<Instance<*>>) = TransactionHolder(_transaction, subInstancesHierarchy, onSuccess)
    }

    private fun <T> transaction(rootInstanceId: InstanceId, instanceId: InstanceId, block: TransactionHolder.() -> T): T {
        val lock = persistence.lockRunningInstanceRootTree(rootInstanceId, instanceId) ?: throw IllegalStateException("Instance not found")
        val holder = TransactionHolder(lock.transaction, lock.instancesHierarchy)
        val res = lock.transaction.use {
            val res = holder.block()
            it.commit()
            res
        }
        for (cb in holder.onSuccess)
            cb()
        return res
    }

    internal fun addWorkflow(workflow: Workflow<*, *>) {
        if (workflow.name in workflows)
            throw IllegalArgumentException("Workflow ${workflow.name} is already defined")

        workflow.ready()

        workflows[workflow.name] = workflow
    }

    internal fun getMove(stateInfo: State.Info): KPlan.Move<Any> {
        val instanceInfo = persistence.getInstanceInfo(stateInfo.instanceId, running = ONLY_RUNNING) ?: throw IllegalStateException("State running instance not found")
        val workflow = getWorkflow(instanceInfo.workflowName)
        val task = workflow.getTaskOf(stateInfo)
        @Suppress("UNCHECKED_CAST") (task as Task<Any, Any, Any>)
        return task.getMove(this, stateInfo)
    }

    internal fun move(fromInfo: State.Info, link: Link, output: Any, toTasks: List<Task<*, *, *>>) {
        transaction(fromInfo.rootInstanceId, fromInfo.instanceId) {
            _move(fromInfo, link, output, toTasks)
        }
    }

    private fun TransactionHolder._move(fromInfo: State.Info, link: Link, output: Any, toTasks: List<Task<*, *, *>>) {
        if (running.none { it.id == fromInfo.id })
            throw IllegalStateException("${fromInfo.taskName} ${fromInfo.id} is not currently running in instance")

        for (task in toTasks)
            if (task.inCls != null && task.inCls.isAssignableFrom(output.javaClass).not())
                throw IllegalArgumentException("${task.name} cannot receive ${output.javaClass.name} (expected a ${task.inCls.name})")

        if (link.id.type == Link.Type.SIGNAL && !link.multi) {
            if (persistence.hasLinkFrom(fromInfo.id, link.id))
                throw IllegalStateException("Cannot emit link ${link.id} as it has already been emitted.")
        }

        val stillRunning = if (link.id.type == Link.Type.SIGNAL) running else running.filter { it.id != fromInfo.id }

        if (toTasks.isEmpty() && stillRunning.isEmpty())
            throw IllegalStateException("Cannot move to no state: instance will become stateless.")

        if (link.id.type == Link.Type.INTERRUPTION)
            _interrupt(fromInfo, dbUpdate = true)

        if (link.id.type != Link.Type.SIGNAL) {
            fromInfo.endDate = Date()
            updateStatesInfoDate(listOf(fromInfo))

            // Emit End event for "from" state
            val fromInput = getStateInput(fromInfo.id)
            onSuccess += { eventBus.fire(KPEventBus.Event.State.Running.End(instancesHierarchy, State(fromInfo, fromInput), workflow.getTaskOf(fromInfo).isBusiness, link.id.type, pastSaved = true)) }
        }

        data class Next(val stateInfo: State.Info, val isNew: Boolean)

        val next = toTasks
            .map {
                val type = it.prepare()
                when (type) {
                    Task.Type.NORMAL -> Next(State.Info(StateId(UUID.randomUUID().toString()), instance.info.id, instance.info.rootInstanceId, it.name), isNew = true)
                    Task.Type.SINGLETON -> {
                        val stateInfo = persistence.getStateInfoOf(instance.info.id, it.name, running = ONLY_RUNNING)
                        if (stateInfo != null)
                            Next(stateInfo, isNew = false)
                        else
                            Next(State.Info(StateId(UUID.randomUUID().toString()), instance.info.id, instance.info.rootInstanceId, it.name), isNew = true)
                    }
                    Task.Type.END, Task.Type.END_SPECIAL -> {
                        _terminateInstance(fromInfo.id, link.id.type, dbUpdate = true)
                        val parentStateId = instance.info.parentStateId
                        if (parentStateId != null)
                            _moveParent(parentStateId, if (type == Task.Type.END_SPECIAL) it.name else "", output)
                        Next(State.Info(StateId(UUID.randomUUID().toString()), instance.info.id, instance.info.rootInstanceId, it.name, endDate = Date(), isEnd = true), isNew = true)
                    }
                }
            }
            .filterNotNull()

        // Compute eventual "Next" tasks
        if (next.isNotEmpty()) {
            putStatesAndLinks(
                states = next.filter { it.isNew }.map { State(it.stateInfo, output) },
                links = next.map { PastLink(fromInfo.id, it.stateInfo.id, it.stateInfo.instanceId, link.id.type, link.id.name, output) }
            )

            val previousStates = persistence.getPreviousStates(fromInfo) + fromInfo.id // from is also a previous state of a next state

            onSuccess += {
                _compute(instance, next.map { State(it.stateInfo, output) }, instancesHierarchy, stillRunning, first = true, previousStateIds = previousStates)
            }
        }
    }

    internal fun removeInstance(rootInstanceId: InstanceId) {
        transaction(rootInstanceId, rootInstanceId) {
            if (instance.info.parentStateId != null || instance.info.rootInstanceId != rootInstanceId) throw IllegalArgumentException("Instance $rootInstanceId is not a root instance")

            _terminateInstance(null, Link.Type.INTERRUPTION, false) // Interrupt every instances/states
            _removeAllPastsForRootInstance()
            deleteInstance(rootInstanceId) // Thanks to cascade instances/states/links are removed too in the whole hierarchy
        }
    }

    internal fun restartAt(state: State<*>) {
        transaction(state.info.rootInstanceId, state.info.instanceId) {
            _restartAt(state)
        }
    }

    private fun TransactionHolder._restartAt(state: State<*>) {
        if (!state.info.isPast)
            throw IllegalArgumentException("Cannot restart at a running state")

        val running = HashSet(running)

        val stopped = HashSet<State.Info>()
        val kept = HashSet<State.Info>()
        _interruptOrDeleteRecursive(state.info, stopped, kept)

        kept.forEach {
            it.endDate = null
        }
        updateStatesInfoDate(kept.toList())

        // Avoid stopping any of the state to keep. Is it needed ?
        stopped -= kept

        running.removeAll { it in stopped }

        val instanceHierarchy = persistence.getInstanceHierarchy(instance.info.id)

        _resetWorkflowsEndDates(state.info)

        val restartedState = _recreateStateAndItsLinks(state)

        onSuccess += {
            val task = workflow.getTaskOf(restartedState.info)
            eventBus.fire(KPEventBus.Event.State.PastRemoved(instanceHierarchy, state, task.isBusiness))
            _compute(instance, listOf(restartedState), instancesHierarchy, running.toList(), first = true)
        }
    }

    // Reset the end dates of all instances and their parent states involved in the given state hierarchy
    private fun TransactionHolder._resetWorkflowsEndDates(stateInfo: State.Info) {
        stateInfo.endDate = null

        val hierarchy = persistence.getInstanceAndStateHierarchy(stateInfo.instanceId)

        // Reset workflow states end date
        hierarchy.states.forEachIndexed { index, it ->
            // We don't filter to avoid index alteration and hierarchy.instances.subList(index, ...) to be wrong
            if (it.info.endDate != null) {
                it.info.endDate = null
                onSuccess += {
                    eventBus.fire(KPEventBus.Event.State.PastRemoved(hierarchy.instances.subList(index, hierarchy.instances.size), it, isBusiness = false))
                    eventBus.fire(KPEventBus.Event.State.Running.Start(hierarchy.instances.subList(index, hierarchy.instances.size), it, isBusiness = false))
                }
            }
        }
        updateStatesInfoDate(hierarchy.states.map { it.info } + stateInfo)

        // Reset instances end dates
        hierarchy.instances.forEachIndexed { index, it ->
            // We don't filter to avoid index alteration and hierarchy.instances.subList(index, ...) to be wrong
            if (it.info.endDate != null) {
                it.info.endDate = null
                onSuccess += {
                    eventBus.fire(KPEventBus.Event.Instance.PastRemoved(hierarchy.instances.subList(index, hierarchy.instances.size)))
                    eventBus.fire(KPEventBus.Event.Instance.Running.Start(hierarchy.instances.subList(index, hierarchy.instances.size)))
                }
            }
        }
        updateInstancesInfoDate(hierarchy.instances.map { it.info })
    }

    // we create a new state to ensure it has a brand new UUID & new start date
    private fun <T : Any> TransactionHolder._recreateStateAndItsLinks(state: State<T>): State<T> {
        val restartedState = State(State.Info(StateId(UUID.randomUUID().toString()), state.info.instanceId, state.info.rootInstanceId, state.info.taskName), state.input)
        val recreatedLinks = ArrayList<PastLink>()

        // Execute deletion across all the states linked
        for (link in persistence.getLinksTo(state.info.id)) {
            val linkInfo = link.value
            val linkOutput = persistence.getLinkOutput(linkInfo.id)
            recreatedLinks.add(PastLink(linkInfo.id.pastStateId, restartedState.info.id, state.info.instanceId, linkInfo.linkId.type, linkInfo.linkId.name, linkOutput ?: Unit))
            deleteLink(linkInfo.id)
        }

        deleteState(state.info.id)
        putStatesAndLinks(listOf(restartedState), recreatedLinks)

        return restartedState
    }

    internal fun retryRunningState(state: State<*>) {
        if (state.info.isPast) throw IllegalArgumentException("Cannot restart a past state")

        val instance = persistence.getInstance(state.info.instanceId) ?: throw IllegalStateException("Could not find instance ${state.info.instanceId}")
        val instancesHierarchy = persistence.getInstanceHierarchy(instance.info.id)
        val running = persistence.getStatesInfoOf(instance.info.id, running = ONLY_RUNNING)

        _compute(instance, listOf(state), instancesHierarchy, running.toList(), first = true)
    }

    private fun TransactionHolder._interruptOrDeleteRecursive(stateInfo: State.Info, stopped: MutableSet<State.Info>, kept: MutableSet<State.Info>) {
        if (!stateInfo.isPast) {
            _interrupt(stateInfo, dbUpdate = false)
            return
        }

        if (stateInfo.isPast && stateInfo.isEnd) {
            // This is a workflow's end, so the recursive deletion will continue on the state linked to the instance parent state
            val instance = persistence.getInstance(stateInfo.instanceId, running = ONLY_PAST)
            if (instance != null) {
                val parentStateId = instance.info.parentStateId
                if (parentStateId != null) {
                    val parentStateInfo = persistence.getStateInfo(parentStateId, running = ONLY_PAST)
                    if (parentStateInfo != null) {
                        subTransaction(instancesHierarchy.subList(1, instancesHierarchy.size))._deleteRecursive(parentStateInfo, stopped, kept, interruptLinked = true)
                    }
                }
            }
        }
        else {
            _deleteRecursive(stateInfo, stopped, kept, interruptLinked = true)
        }
    }

    private fun TransactionHolder._deleteRecursive(stateInfo: State.Info, stopped: MutableSet<State.Info>, kept: MutableSet<State.Info>, interruptLinked: Boolean) {
        stopped += stateInfo

        // Execute deletion across all the states linked
        for ((linkInfo, linkedStateInfo) in persistence.getLinksFrom(stateInfo.id)) {
            if (interruptLinked) {
                _interruptOrDeleteRecursive(linkedStateInfo, stopped, kept)
            }
            else {
                _deleteRecursive(linkedStateInfo, stopped, kept, interruptLinked = false)
            }

            // When the linked state is a SubWorkflow
            if (workflow.getTaskOf(linkedStateInfo).hasSubWorkflow) {
                val instance = persistence.getInstanceOf(linkedStateInfo.id)
                if (instance != null) {
                    val startStateInfo = persistence.getStateInfo(instance.info.startStateId)
                    if (startStateInfo != null) {
                        val subInstancesHierarchy = listOf(instance) + instancesHierarchy
                        subTransaction(subInstancesHierarchy).apply {
                            _deleteRecursive(startStateInfo, stopped, kept, interruptLinked = false)
                            if (startStateInfo.isPast) {
                                val startStateInput = getStateInput(linkedStateInfo.id)
                                onSuccess += { eventBus.fire(KPEventBus.Event.State.PastRemoved(subInstancesHierarchy, State(startStateInfo, startStateInput), workflow.getTaskOf(startStateInfo).isBusiness)) }
                            }

                            deleteState(startStateInfo.id)
                        }
                    }
                    deleteInstance(instance.info.id)
                }
            }

            deleteLink(linkInfo.id)

            // When the linked state is a "normal" task (not a workflow)
            val links = persistence.getStateIdsLinkedTo(linkedStateInfo.id)
            // We can delete the linkedState ONLY if there is no preceding state that has a reference on it (and will not be stopped)
            if ((links - stopped.map { it.id }).isEmpty()) {
                kept -= linkedStateInfo
                if (linkedStateInfo.isPast) {
                    val linkedStateInput = getStateInput(linkedStateInfo.id)
                    val instanceHierarchy = persistence.getInstanceHierarchy(instance.info.id)
                    onSuccess += { eventBus.fire(KPEventBus.Event.State.PastRemoved(instanceHierarchy, State(linkedStateInfo, linkedStateInput), workflow.getTaskOf(linkedStateInfo).isBusiness)) }
                }
                deleteState(linkedStateInfo.id)
            }
            else {
                kept += linkedStateInfo
            }
        }
    }

    private fun TransactionHolder._terminateInstance(keepStateId: StateId?, linkType: Link.Type, dbUpdate: Boolean) {

        running.filter { it.id != keepStateId }.forEach {
            _interrupt(it, dbUpdate)
        }

        if (dbUpdate) {
            instance.info.endDate = Date()
            updateInstancesInfoDate(listOf(instance.info))
        }

        onSuccess += { eventBus.fire(KPEventBus.Event.Instance.Running.End(instancesHierarchy, linkType, dbUpdate)) }
    }

    private fun TransactionHolder._moveParent(parentStateId: StateId, linkName: String, output: Any) {
        if (instancesHierarchy.size < 2) throw IllegalStateException("Instance declares a parent but it was not locked")

        val parentStateInfo = persistence.getStateInfo(parentStateId, running = ONLY_RUNNING) ?: throw IllegalStateException("Parent state not found")
        val parentInstance = instancesHierarchy[1]

        if (parentStateInfo.instanceId != parentInstance.info.id) throw IllegalStateException("Instance declares a parent but another was locked")

        val parentWorkflow = getWorkflow(parentInstance.info.workflowName)
        val parentTask = parentWorkflow.getTaskOf(parentStateInfo)

        // https://youtrack.jetbrains.com/issue/KT-13028
        @Suppress("UNCHECKED_CAST") (parentTask as WorkflowTask<Any, *, Any, *>)

        val linkId = Link.Id(Link.Type.FLOW, linkName)

        val (link, nexts) = parentTask.findLink(linkId)

        subTransaction(instancesHierarchy.subList(1, instancesHierarchy.size))._move(parentStateInfo, link, output, nexts)
    }

    internal fun TransactionHolder._interrupt(stateInfo: State.Info, dbUpdate: Boolean) {
        if (dbUpdate) {
            stateInfo.endDate = Date()
            updateStatesInfoDate(listOf(stateInfo))
        }

        val task = workflow.getTaskOf(stateInfo)

        val input = getStateInput(stateInfo.id)
        val instanceHierarchy = persistence.getInstanceHierarchy(instance.info.id)
        onSuccess += { eventBus.fire(KPEventBus.Event.State.Running.End(instanceHierarchy, State(stateInfo, input), task.isBusiness, Link.Type.INTERRUPTION, dbUpdate)) }

        if (task.hasSubWorkflow) {
            val instance = persistence.getInstanceOf(stateInfo.id)
            if (instance != null) {
                subTransaction(listOf(instance) + instancesHierarchy)._terminateInstance(null, Link.Type.INTERRUPTION, dbUpdate)
            }
        }
    }

    // Send [KPEventBus.Event.Instance.PastRemoved] & [KPEventBus.Event.State.PastRemoved] for all elements of the instance, recursively
    private fun TransactionHolder._removeAllPastsForRootInstance() {
        val allPastStates = persistence.getRecursiveStatesOfRoot(instance.info.id, ONLY_PAST)

        allPastStates.forEach {
            val instanceInfo = persistence.getInstanceInfo(it.info.instanceId) ?: throw IllegalArgumentException("Instance ${it.info.id} not found.")
            val instanceHierarchy = persistence.getInstanceHierarchy(instanceInfo.id)

            val instanceWorkflow = getWorkflow(instanceInfo.workflowName)
            val task = instanceWorkflow.getTaskOf(it.info)

            onSuccess += { eventBus.fire(KPEventBus.Event.State.PastRemoved(instanceHierarchy, it, task.isBusiness)) }

            if (task.hasSubWorkflow) {
                onSuccess += { eventBus.fire(KPEventBus.Event.Instance.PastRemoved(instanceHierarchy)) }
            }
        }
    }

    internal fun <WI : Any> create(workflow: Workflow<WI, *>, input: Instance.Input<WI>, parentState: State<*>?): Pair<Instance<WI>, () -> Unit> {
        val iid = InstanceId(UUID.randomUUID().toString())
        val sid = StateId(UUID.randomUUID().toString())
        val riid = parentState?.info?.rootInstanceId ?: iid

        val startTask = workflow.startTask ?: throw IllegalStateException("Workflow ${workflow.name} has no start task")

        val instance = Instance(Instance.Info(iid, riid, workflow.name, sid, parentState?.info?.instanceId, parentState?.info?.id), input)
        val state = State(State.Info(sid, iid, riid, startTask.name), Unit)

        persistence.putInstanceAndState(instance, state)

        val instanceHierarchy = persistence.getInstanceHierarchy(instance.info.id)

        eventBus.fire(KPEventBus.Event.Instance.Running.Start(instanceHierarchy))

        return instance to { _compute(instance, listOf(state), instanceHierarchy, emptyList(), first = true) }
    }

    private fun _compute(instance: Instance<*>, states: List<State<*>>, instanceHierarchy: List<Instance<*>>, running: List<State.Info>, first: Boolean, previousStateIds: Set<StateId>? = null) {
        val workflow = getWorkflow(instance.info.workflowName)

        states
            .forEach {
                eventBus.fire(KPEventBus.Event.State.Running.Start(instanceHierarchy, it, workflow.getTaskOf(it.info).isBusiness, previousStateIds))
            }

        for (state in states) {
            val task = workflow.getTaskOf(state.info)
            @Suppress("UNCHECKED_CAST") (task as Task<Any, *, Any>)
            task.compute(this, instance, state, running, first)
        }
    }

    internal fun compute(instance: Instance<*>) {
        val instanceHierarchy = persistence.getInstanceHierarchy(instance.info.id)
        val states = persistence.getStatesOf(instance.info.id, running = ONLY_RUNNING)

        _compute(instance, states, instanceHierarchy, states.map { it.info }, first = false)
    }
}