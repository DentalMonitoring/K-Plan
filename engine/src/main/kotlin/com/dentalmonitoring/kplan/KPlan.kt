package com.dentalmonitoring.kplan

import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.model.StateId
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.RunningState
import com.dentalmonitoring.kplan.model.RunningState.ALL
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.Workflow

interface KPlan {

    interface Move<in O : Any> {

        fun flow(result: O)

        fun flow(name: String, result: Any)

        fun signal(name: String, result: Any)

        fun interrupt(name: String, result: Any)

        fun onInterrupt(callback: () -> Unit)
        val isInterrupted: Boolean
    }

    fun <I : Any, O : Any> new(name: String, block: Workflow.Builder<I, O>.() -> Unit): Workflow<I, O>

    fun <I : Any> create(workflow: Workflow<I, *>, input: Instance.Input<I>): Pair<Instance<I>, () -> Unit>

    fun getMove(stateInfo: State.Info): Move<Any>

    fun getMove(instanceInfo: Instance.Info): Move<Any>

    fun getStateInfo(stateId: StateId, running: RunningState = ALL): State.Info?

    fun getInstance(instanceId: InstanceId, running: RunningState = ALL): Instance<*>?

    fun getRootInstanceTreeList(rootInstanceId: InstanceId, running: RunningState = ALL): List<Instance.Info>

    /** Retrieve all the states for the given instance.
     *  Could be filtered on business task if asked.
     *  @param instanceId the instance id for which we want to get the states
     *  @param businessOnly to filter the states by business flag
     *  @return the instance's states
     */
    fun getStatesOf(instanceId: InstanceId, businessOnly: Boolean = false): List<State<*>>

    /** Retrieve all the states for the given root instance & its children.
     *  Could be filtered on business task if asked.
     *  @param rootInstanceId the root instance id for which we want to get the states
     *  @param businessOnly to filter the states by business flag
     *  @return the states of the given root instance & its children
     */
    fun getRecursiveStatesOfRoot(rootInstanceId: InstanceId, businessOnly: Boolean = false): List<State<*>>

    /** Restart the workflow instance to a previous past state (that will become a running state)
     *  It will also suppress all the states from the state to restart to the current state.
     *  @param stateId the state where to restart the workflow instance
     *  @throws IllegalArgumentException if the given state is not past
     */
    fun restartAt(stateId: StateId)

    /** Retry to compute a currently running state
     *  Could be useful when a server-side task did a compute and failed.
     *  @param stateId the state to re-compute the task
     *  @throws IllegalArgumentException if the given state is not running
     */
    fun retryRunningState(stateId: StateId)

    // TODO: doc
    fun computeAll()

    /**
     * Remove a root instance and its associated states/links/subinstances.
     * @param rootInstanceId the instance id to remove MUST refer to a root instance
     * @throws IllegalArgumentException if the instanceID is not a root one
     */
    fun removeInstance(rootInstanceId: InstanceId)

    /** Generate the graphical view of a workflow (optionally a workflow instance)
     *  using [Graphviz](http://www.graphviz.org/Documentation.php), written in [Dot language](http://www.graphviz.org/pdf/dotguide.pdf)
     *  @param workflowName the workflow to generate
     *  @param instanceId the (optional) instance id to get the instance current state
     *  @return the graphical description in Dot language
     */
    fun generateGraphviz(workflowName: String, instanceId: InstanceId? = null): String?

}
