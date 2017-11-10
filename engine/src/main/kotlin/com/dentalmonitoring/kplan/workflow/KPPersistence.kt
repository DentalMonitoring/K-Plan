package com.dentalmonitoring.kplan.workflow

import com.dentalmonitoring.kplan.InstanceRequest
import com.dentalmonitoring.kplan.model.*
import com.dentalmonitoring.kplan.model.RunningState.ALL
import java.io.Closeable

interface KPPersistence {
    fun putInstanceAndState(instance: Instance<*>, state: State<*>): Unit

    fun getStateInfo(id: StateId, running: RunningState = ALL): State.Info?
    fun getStateInput(id: StateId): Any?

    fun getStateInfoOf(instanceId: InstanceId, taskName: String, running: RunningState = ALL): State.Info?

    fun getInstance(id: InstanceId, running: RunningState = ALL): Instance<*>?
    fun getInstanceInfo(id: InstanceId, running: RunningState = ALL): Instance.Info?

    /**
     * Retrieve the states preceding a state.
     * Note: it is limited to the same workflow
     * @param state the state to search the ancestors
     * @return a set of all the state IDs preceding the given state, is none it returns an empty set
     */
    fun getPreviousStates(state: State.Info): Set<StateId>

    /** Retrieve the instances hierarchy from the [instanceId]
     * @param instanceId the instance to start the hierarchy creation
     * @return the instance hierarchy as a list (from [instanceId] to the root)
     */
    fun getInstanceHierarchy(instanceId: InstanceId): List<Instance<*>>

    /** Retrieve the instances and the associated states hierarchies from the [instanceId]
     * The [Hierarchy] object will be populated with N instances and N-1 states (because there is no state associated to root instance)
     * The [Hierarchy] object returned represents the instances hierarchy as an ordered list (from [instanceId] instance to the root]
     *
     * @param instanceId the instance to start the hierarchies creation
     * @return the instances and states hierarchies as two lists (from [instanceId] to the root) wrapped in a [Hierarchy] object
     */
    fun getInstanceAndStateHierarchy(instanceId: InstanceId): Hierarchy

    data class Hierarchy(val instances: List<Instance<*>>, val states: List<State<*>>)

    fun findInstances(request: InstanceRequest, limit: Int, offsetKey: String? = null): Pair<List<Instance<*>>, String?>

    fun getStatesInfoOf(instanceId: InstanceId, running: RunningState = ALL): List<State.Info>
    fun getRecursiveStatesInfoOfRoot(rootInstanceId: InstanceId, running: RunningState = ALL): List<State.Info>
    fun getStatesOf(instanceId: InstanceId, running: RunningState = ALL): List<State<*>>
    fun getRecursiveStatesOfRoot(rootInstanceId: InstanceId, running: RunningState = ALL): List<State<*>>

    fun getInstanceOf(parentStateId: StateId): Instance<*>?

    fun getRootInstanceList(rootInstanceId: InstanceId, running: RunningState = ALL): List<Instance.Info>

    fun hasLinkFrom(stateId: StateId, linkId: Link.Id?): Boolean
    fun getLinksFrom(stateId: StateId): Map<PastLink.Info, State.Info>

    fun getStateIdsLinkedTo(stateId: StateId): List<StateId>
    fun getLinksTo(stateId: StateId): Map<State.Info, PastLink.Info>

    fun getLinkOutput(id: PastLink.Id): Any?

    interface TransactionBase : Closeable {

        fun putStatesAndLinks(states: List<State<*>>, links: List<PastLink>)

        fun updateStatesInfoDate(states: List<State.Info>)

        fun updateInstancesInfoDate(instances: List<Instance.Info>)

        fun deleteState(stateId: StateId)

        fun deleteLink(id: PastLink.Id)

        fun deleteInstance(instanceId: InstanceId)
    }

    interface Transaction : TransactionBase {

        fun commit()
    }

    data class LockInstanceResult(val instancesHierarchy: List<Instance<*>>, val transaction: Transaction)

    fun lockRunningInstanceRootTree(rootInstanceId: InstanceId, instanceId: InstanceId): LockInstanceResult?
}
