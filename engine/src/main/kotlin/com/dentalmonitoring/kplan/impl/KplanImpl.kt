package com.dentalmonitoring.kplan.impl

import com.dentalmonitoring.kplan.InstanceRequest
import com.dentalmonitoring.kplan.KPlan
import com.dentalmonitoring.kplan.generateGraphViz
import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.model.StateId
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.RunningState
import com.dentalmonitoring.kplan.model.RunningState.ONLY_RUNNING
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEngine
import com.dentalmonitoring.kplan.workflow.KPEventBus
import com.dentalmonitoring.kplan.workflow.KPPersistence
import com.dentalmonitoring.kplan.workflow.Workflow
import org.slf4j.LoggerFactory
import java.util.*

class KplanImpl(persistence: KPPersistence, val eventBus: KPEventBus) : KPlan {
    private val _logger = LoggerFactory.getLogger(KplanImpl::class.java)

    override fun <I : Any, O : Any> new(name: String, block: Workflow.Builder<I, O>.() -> Unit): Workflow<I, O> {
        val workflow = Workflow.Builder<I, O>(name).apply(block)._workflow
        _engine.addWorkflow(workflow)
        return workflow
    }

    override fun <I : Any> create(workflow: Workflow<I, *>, input: Instance.Input<I>): Pair<Instance<I>, () -> Unit> = _engine.create(workflow, input, null)

    override fun getMove(stateInfo: State.Info) = _engine.getMove(stateInfo)

    override fun getMove(instanceInfo: Instance.Info): KPlan.Move<Any> {
        if (instanceInfo.endDate != null)
            throw IllegalStateException("Instance is not running")
        val parentStateId = instanceInfo.parentStateId ?: throw IllegalStateException("Instance does not have a parent instance")
        val stateInfo = _engine.persistence.getStateInfo(parentStateId, running = ONLY_RUNNING) ?: throw IllegalStateException("Could not find parent state")
        return _engine.getMove(stateInfo)
    }

    override fun getStateInfo(stateId: StateId, running: RunningState) = _engine.persistence.getStateInfo(stateId, running)

    override fun getInstance(instanceId: InstanceId, running: RunningState) = _engine.persistence.getInstance(instanceId, running)

    override fun getRootInstanceTreeList(rootInstanceId: InstanceId, running: RunningState) = _engine.persistence.getRootInstanceList(rootInstanceId, running)

    override fun restartAt(stateId: StateId) {
        val state = getState(stateId)
        if (state != null)
            _engine.restartAt(state)
        else
            _logger.warn("State with id=$stateId is not found in DB")
    }

    override fun retryRunningState(stateId: StateId) {
        val state = getState(stateId)
        if (state != null)
            _engine.retryRunningState(state)
        else
            _logger.warn("State with id=$stateId is not found in DB")
    }

    private fun getState(stateId: StateId) = _engine.persistence.getStateInfo(stateId)?.let { info -> _engine.persistence.getStateInput(stateId)?.let { input -> State(info, input) } }

    override fun computeAll() {
        var offsetKey: String? = null
        do {
            val (list, nextOffsetKey) = _engine.persistence.findInstances(InstanceRequest(), 50, offsetKey)

            list.forEach { compute(it) }

            offsetKey = nextOffsetKey
        } while (offsetKey != null)
    }

    override fun generateGraphviz(workflowName: String, instanceId: InstanceId?) = workflows[workflowName]?.generateGraphViz(_engine.persistence, instanceId)

    // TODO: no usage in production (only for test purposes), should we remove it ?
    internal fun findInstances(request: InstanceRequest, limit: Int = 0, offsetKey: String? = null) = _engine.persistence.findInstances(request, limit, offsetKey = offsetKey)

    internal fun compute(instance: Instance<*>) = _engine.compute(instance)

    internal val workflows: Map<String, Workflow<*, *>> get() = HashMap(_engine.workflows)

    override fun getRecursiveStatesOfRoot(rootInstanceId: InstanceId, businessOnly: Boolean): List<State<*>> {
        val states = _engine.persistence.getRecursiveStatesOfRoot(rootInstanceId)
        return if (businessOnly) states.filterBusiness() else states
    }

    override fun getStatesOf(instanceId: InstanceId, businessOnly: Boolean): List<State<*>> {
        val states = _engine.persistence.getStatesOf(instanceId)
        return if (businessOnly) states.filterBusiness() else states
    }

    private fun List<State<*>>.filterBusiness(): List<State<*>> {
        val wfNameMap = HashMap<InstanceId, String>()

        // Filtering the states related to business tasks
        return filter {
            val wfName = wfNameMap.getOrPut(it.info.instanceId) {
                _engine.persistence.getInstanceInfo(it.info.instanceId)?.workflowName ?: ""
            }
            _engine.workflows[wfName]?.tasks?.get(it.info.taskName)?.isBusiness ?: false
        }
    }

    private val _engine = KPEngine(persistence, eventBus)

    override fun removeInstance(rootInstanceId: InstanceId) {
        _engine.removeInstance(rootInstanceId)
    }
}