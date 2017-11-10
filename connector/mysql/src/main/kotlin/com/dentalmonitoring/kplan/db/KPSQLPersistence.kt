package com.dentalmonitoring.kplan.db

import com.google.gson.Gson
import com.dentalmonitoring.kplan.InstanceRequest
import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.model.StateId
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.PastLink
import com.dentalmonitoring.kplan.model.RunningState
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPPersistence
import com.dentalmonitoring.kplan.model.Link
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import javax.sql.DataSource

private val _logger = LoggerFactory.getLogger(KPSQLPersistence::class.java)

private fun Connection.close(exception: Throwable?) {
    try {
        close()
    }
    catch (closeException: Throwable) {
        if (exception != null)
            exception.addSuppressed(closeException)
        else
            throw closeException
    }
}


private fun Connection.resetClose(exception: Throwable?) {
    try {
        autoCommit = true
    }
    catch (resetException: Throwable) {
        if (exception != null)
            exception.addSuppressed(resetException)
        else {
            close(resetException)
            throw resetException
        }
    }

    close(exception)
}

private fun Connection.rollbackResetClose(exception: Throwable?) {
    try {
        rollback()
    }
    catch (rollbackException: Throwable) {
        if (exception != null)
            exception.addSuppressed(rollbackException)
        else {
            resetClose(rollbackException)
            throw rollbackException
        }
    }

    resetClose(exception)
}

private fun <T> Connection.transaction(block: Connection.() -> T): T {
    autoCommit = false

    val ret = try {
        block()
    }
    catch (exception: Exception) {
        rollbackResetClose(exception)

        throw exception
    }

    resetClose(null)

    return ret
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> T.serializableJavaClass(): Class<T> {
    if (this is Map<*, *>)
        return Map::class.java as Class<T>
    if (this is List<*>)
        return List::class.java as Class<T>
    if (this is Set<*>)
        return Set::class.java as Class<T>
    return javaClass
}

// ==========================================

internal object InstanceSQL {
    const val Table = "k_instance"

    const val FId = "i_id"
    const val FRootInstanceId = "i_root_instance_id"
    const val FWorkflowName = "i_workflow"
    const val FStartStateId = "i_start_state_id"
    const val FParentInstanceId = "i_parent_instance_id"
    const val FParentStateId = "i_parent_state_id"
    const val FStartDate = "i_start_date"
    const val FEndDate = "i_end_date"
    const val FInputInt = "i_input_int"
    const val FInputStr = "i_input_str"
    const val FInputObjType = "i_input_obj_type"
    const val FInputObj = "i_input_obj"

    const val SQLInsert = """
        INSERT INTO `${Table}` (
            `${FId}`,
            `${FRootInstanceId}`,
            `${FWorkflowName}`,
            `${FStartStateId}`,
            `${FParentInstanceId}`,
            `${FParentStateId}`,
            `${FStartDate}`,
            `${FEndDate}`,
            `${FInputInt}`,
            `${FInputStr}`,
            `${FInputObjType}`,
            `${FInputObj}`
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
    """

    const val infoFields = """
        `${FId}`,
        `${FRootInstanceId}`,
        `${FWorkflowName}`,
        `${FStartStateId}`,
        `${FParentInstanceId}`,
        `${FParentStateId}`,
        `${FStartDate}`,
        `${FEndDate}`
    """

    const val inputFields = """
        `${FInputInt}`,
        `${FInputStr}`,
        `${FInputObjType}`,
        `${FInputObj}`
    """
}

internal object StateSQL {
    const val Table = "k_state"

    const val FId = "s_id"
    const val FInstanceId = "s_instance_id"
    const val FRootInstanceId = "s_root_instance_id"
    const val FTaskName = "s_task"
    const val FStartDate = "s_start_date"
    const val FEndDate = "s_end_date"
    const val FIsEnd = "s_is_end"
    const val FInputType = "s_input_type"
    const val FInput = "s_input"

    const val SQLInsert = """
        INSERT INTO `${Table}` (
            `${FId}`,
            `${FInstanceId}`,
            `${FRootInstanceId}`,
            `${FTaskName}`,
            `${FStartDate}`,
            `${FEndDate}`,
            `${FIsEnd}`,
            `${FInputType}`,
            `${FInput}`
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
    """

    const val infoFields = """
        `${FId}`,
        `${FInstanceId}`,
        `${FRootInstanceId}`,
        `${FTaskName}`,
        `${FStartDate}`,
        `${FEndDate}`,
        `${FIsEnd}`
    """

    const val inputFields = """
        `${FInputType}`,
        `${FInput}`
    """
}

internal object PastLinkSQL {
    const val Table = "k_link"

    const val FPastStateId = "l_past_state_id"
    const val FNextStateId = "l_next_state_id"
    const val FInstanceId = "l_instance_id"
    const val FLinkType = "l_type"
    const val FLinkName = "l_name"
    const val FOutputType = "l_output_type"
    const val FOutput = "l_output"

    const val SQLInsert = """
        INSERT INTO `${Table}` (
            `${FPastStateId}`,
            `${FNextStateId}`,
            `${FInstanceId}`,
            `${FLinkType}`,
            `${FLinkName}`,
            `${FOutputType}`,
            `${FOutput}`
        )
        VALUES (?, ?, ?, ?, ?, ?, ?);
    """

    const val infoFields = """
        `${FPastStateId}`,
        `${FNextStateId}`,
        `${FInstanceId}`,
        `${FLinkType}`,
        `${FLinkName}`,
        `${FOutputType}`
    """

    const val outputFields = """
        `${FOutputType}`,
        `${FOutput}`
    """
}


private fun ResultSet._getInstance(gson: Gson): Instance<*>? = with(InstanceSQL) {
    try {
        Instance(
                info = _getInstanceInfo(),
                input = _getInstanceInput(gson)
        )
    }
    catch (e: Throwable) {
        _logger.error("Error when reading instance from DB", e)
        null
    }
}

private fun ResultSet._getInstanceInfo() = with(InstanceSQL) {
    Instance.Info(
        id = InstanceId(getString(FId)),
        rootInstanceId = InstanceId(getString(FRootInstanceId)),
        workflowName = getString(FWorkflowName),
        startStateId = StateId(getString(FStartStateId)),
        parentInstanceId = if (getString(FParentInstanceId) != null) InstanceId(getString(FParentInstanceId)) else null,
        parentStateId = if (getString(FParentStateId) != null) StateId(getString(FParentStateId)) else null,
        startDate = getTimestamp(FStartDate),
        endDate = getTimestamp(FEndDate)
    )
}

private fun ResultSet._getInstanceInput(gson: Gson) = with(InstanceSQL) {
    val type = Class.forName(getString(FInputObjType))
    Instance.Input(
        int = getInt(FInputInt),
        str = getString(FInputStr),
        obj = gson.fromJson(getString(FInputObj), type)
    )
}

//private fun ResultSet._getState(gson: Gson) = with (StateSQL) {
//    State(
//            info  = _getStateInfo(),
//            input = _getStateInput(gson)
//    )
//}

private fun ResultSet._getStateInput(gson: Gson) = with(StateSQL) {
    val type = Class.forName(getString(FInputType))
    gson.fromJson(getString(FInput), type)
}

private fun ResultSet._getStateInfo() = with(StateSQL) {
    State.Info(
        id = StateId(getString(FId)),
        rootInstanceId = InstanceId(getString(FRootInstanceId)),
        instanceId = InstanceId(getString(FInstanceId)),
        taskName = getString(FTaskName),
        startDate = getTimestamp(FStartDate),
        endDate = getTimestamp(FEndDate),
        isEnd = getBoolean(FIsEnd)
    )
}

//private fun ResultSet._getPastLink(gson: Gson) = with (PastLinkSQL) {
//    PastLink(
//            info   = _getPastLinkInfo(),
//            output = _getPastLinkOutput(gson)
//    )
//}

private fun ResultSet._getPastLinkInfo() = with(PastLinkSQL) {
    PastLink.Info(
        id = PastLink.Id(
            pastStateId = StateId(getString(FPastStateId)),
            nextStateId = StateId(getString(FNextStateId)),
            instanceId = InstanceId(getString(FInstanceId))
        ),
        linkId = Link.Id(
            type = Link.Type.valueOf(getString(FLinkType)),
            name = getString(FLinkName)
        )
    )
}

private fun ResultSet._getPastLinkOutput(gson: Gson) = with(PastLinkSQL) {
    val type = Class.forName(getString(FOutputType))
    gson.fromJson(getString(FOutput), type)
}


private fun PreparedStatement._insert(link: PastLink, gson: Gson) {
    setString(1, link.info.id.pastStateId.id)
    setString(2, link.info.id.nextStateId.id)
    setString(3, link.info.id.instanceId.id)
    setString(4, link.info.linkId.type.toString())
    setString(5, link.info.linkId.name)
    setString(6, link.output.serializableJavaClass().name)
    setString(7, gson.toJson(link.output))
}

private fun PreparedStatement._insert(state: State<*>, gson: Gson) {
    setString(1, state.info.id.id)
    setString(2, state.info.instanceId.id)
    setString(3, state.info.rootInstanceId.id)
    setString(4, state.info.taskName)
    setTimestamp(5, java.sql.Timestamp(state.info.startDate.time))
    val endDate = state.info.endDate
    setTimestamp(6, if (endDate != null) java.sql.Timestamp(endDate.time) else null)
    setBoolean(7, state.info.isEnd)
    setString(8, state.input.serializableJavaClass().name)
    setString(9, gson.toJson(state.input))
}

private fun PreparedStatement._insert(instance: Instance<*>, gson: Gson) {
    setString(1, instance.info.id.id)
    setString(2, instance.info.rootInstanceId.id)
    setString(3, instance.info.workflowName)
    setString(4, instance.info.startStateId.id)
    setString(5, instance.info.parentInstanceId?.id)
    setString(6, instance.info.parentStateId?.id)
    setTimestamp(7, java.sql.Timestamp(instance.info.startDate.time))
    val endDate = instance.info.endDate
    setTimestamp(8, if (endDate != null) java.sql.Timestamp(endDate.time) else null)
    setInt(9, instance.input.int)
    setString(10, instance.input.str)
    setString(11, instance.input.obj.serializableJavaClass().name)
    setString(12, gson.toJson(instance.input.obj))
}


// ==========================================

fun <T : Any> ResultSet.toMappedSequence(transform: (ResultSet) -> T?) = generateSequence {
    if (next())
        transform(this)
    else
        null
}

class KPSQLPersistence(private val _ds: DataSource, private val _gson: Gson) : KPPersistence {

    override fun putInstanceAndState(instance: Instance<*>, state: State<*>) {
        _ds.connection.transaction {
            prepareStatement(InstanceSQL.SQLInsert).use {
                it._insert(instance, _gson)
                it.executeUpdate()
            }

            prepareStatement(StateSQL.SQLInsert).use {
                it._insert(state, _gson)
                it.executeUpdate()
            }

            commit()
        }
    }

    private fun _andRunning(running: RunningState, field: String, hasWhere: Boolean = true): String {
        return when (running) {
            RunningState.ALL -> ""
            RunningState.ONLY_PAST -> _hasWhereString(hasWhere) + " `$field` IS NOT NULL"
            RunningState.ONLY_RUNNING -> _hasWhereString(hasWhere) + " `$field` IS NULL"
        }
    }

    private fun _hasWhereString(hasWhere: Boolean = true) = if (hasWhere) " AND" else " WHERE"

    override fun getStateInfo(id: StateId, running: RunningState): State.Info? = with(StateSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT ${infoFields} FROM `${Table}` WHERE `${FId}` = ?" + _andRunning(running, FEndDate)).use {
                it.setString(1, id.id)
                it.executeQuery().toMappedSequence { it._getStateInfo() }.firstOrNull()
            }
        }
    }

    override fun getStateInput(id: StateId): Any? = with(StateSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT ${inputFields} FROM `${Table}` WHERE `${FId}` = ?").use {
                it.setString(1, id.id)
                it.executeQuery().toMappedSequence { it._getStateInput(_gson) }.firstOrNull()
            }
        }
    }

    override fun getStateInfoOf(instanceId: InstanceId, taskName: String, running: RunningState): State.Info? = with(StateSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT ${infoFields} FROM `${Table}` WHERE `${FInstanceId}` = ? AND `${FTaskName}` = ?" + _andRunning(running, FEndDate)).use {
                it.setString(1, instanceId.id)
                it.setString(2, taskName)
                it.executeQuery().toMappedSequence { it._getStateInfo() }.firstOrNull()
            }
        }
    }

    override fun getInstance(id: InstanceId, running: RunningState): Instance<*>? = with(InstanceSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT * FROM `${Table}` WHERE `${FId}` = ?" + _andRunning(running, FEndDate)).use {
                it.setString(1, id.id)
                it.executeQuery().toMappedSequence { it._getInstance(_gson) }.firstOrNull()
            }
        }
    }

    override fun getInstanceInfo(id: InstanceId, running: RunningState): Instance.Info? = with(InstanceSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT ${infoFields} FROM `${Table}` WHERE `${FId}` = ?" + _andRunning(running, FEndDate)).use {
                it.setString(1, id.id)
                it.executeQuery().use { it.toMappedSequence { it._getInstanceInfo() }.firstOrNull() }
            }
        }
    }

    override fun getPreviousStates(state: State.Info): Set<StateId> = _ds.connection.use { _getPreviousStates(it, state) }

    private fun _getPreviousStates(con: Connection, state: State.Info): Set<StateId> {
        val allLinks = con.prepareStatement(with(PastLinkSQL) { "SELECT `${FPastStateId}`, `${FNextStateId}` FROM `${Table}` WHERE `${FInstanceId}` = ?" }).use {
            it.setString(1, state.instanceId.id)
            it.executeQuery().use { it.toMappedSequence { StateId(it.getString(PastLinkSQL.FPastStateId)) to StateId(it.getString(PastLinkSQL.FNextStateId)) }.toList() }
        }

        return extractAncestorsRecursively(setOf(state.id), allLinks)
    }

    private fun extractAncestorsRecursively(nextStates: Set<StateId>, allLinks: List<Pair<StateId, StateId>>): Set<StateId> {
        val pastStates = allLinks
            .asSequence()
            .filter { it.second in nextStates }
            .map { it.first }
            .toSet()

        if (pastStates.isEmpty())
            return emptySet()
        else
            return pastStates + extractAncestorsRecursively(pastStates, allLinks)
    }


    /** Retrieve the instances hierarchy
     *  @param con
     *  @param id the instance id
     *  @return the instances hierarchy as an ordered list (from [id] instance to the root]
     */
    private fun _getInstanceHierarchy(con: Connection, id: InstanceId): List<Instance<*>> {
        val allInstancesWithSameRootById = con.prepareStatement(with(InstanceSQL) { "SELECT * FROM ${Table} WHERE ${FRootInstanceId} = (SELECT ${FRootInstanceId} FROM ${Table} WHERE ${FId} = ?)" }).use { stmt ->
            stmt.setString(1, id.id)
            stmt.executeQuery().toMappedSequence { it._getInstance(_gson) }.map { it.info.id to it }.toMap()
        }

        return extractAncestorsRecursively(id, allInstancesWithSameRootById)
    }

    private tailrec fun extractAncestorsRecursively(currentInstanceId: InstanceId, instancesById: Map<InstanceId, Instance<*>>, result: List<Instance<*>> = emptyList()): List<Instance<*>> {
        val currentInstance = instancesById[currentInstanceId] ?: throw IllegalStateException("Instance ${currentInstanceId.id} not found")
        val ancestorInstanceId = currentInstance.info.parentInstanceId
        if (ancestorInstanceId == null)
            return result + currentInstance
        else
            return extractAncestorsRecursively(ancestorInstanceId, instancesById, result + currentInstance)
    }

    override fun getInstanceHierarchy(instanceId: InstanceId): List<Instance<*>> = _ds.connection.use { _getInstanceHierarchy(it, instanceId) }

    override fun getInstanceAndStateHierarchy(instanceId: InstanceId): KPPersistence.Hierarchy {
        _ds.connection.use { con ->
            val instances = _getInstanceHierarchy(con, instanceId)
            val rootInstanceId = instances.first().info.rootInstanceId.id

            val allStatesById = con.prepareStatement(with(StateSQL) { "SELECT * FROM ${Table} WHERE ${FRootInstanceId} = ?" }).use { stmt ->
                stmt.setString(1, rootInstanceId)
                stmt.executeQuery().toMappedSequence { State(it._getStateInfo(), it._getStateInput(_gson)) }.map { it.info.id.id to it }.toMap()
            }

            val states = instances.dropLast(1) // because last instance is the root and has no associated state
                .asSequence()
                .map { it.info.parentStateId?.id ?: throw IllegalStateException("Instance ${it.info.id.id} has no parent state while it should") }
                .map { allStatesById[it] ?: throw IllegalStateException("State $it not found") }
                .toList()

            return KPPersistence.Hierarchy(instances, states)
        }
    }

    override fun findInstances(request: InstanceRequest, limit: Int, offsetKey: String?): Pair<List<Instance<*>>, String?> = with(InstanceSQL) {

        val offset = if (offsetKey != null) String(Base64.getDecoder().decode(offsetKey)).toLong() else 0

        val args = LinkedList<PreparedStatement.(Int) -> Unit>()

        val select = buildString {
            append("SELECT * FROM `${Table}`")
            var hasWhere = false
            if (request.wfName != null || request.inputStr != null || request.inputInt != null || request.root != null) {
                append(" WHERE")
                hasWhere = true
                val wheres = LinkedList<String>()
                if (request.wfName != null) {
                    wheres += " ${FWorkflowName} = ?"
                    args += { setString(it, request.wfName) }
                }
                val root = request.root
                if (root != null) {
                    if (root)
                        wheres += " ${FParentStateId} IS NULL"
                    else
                        wheres += " ${FParentStateId} IS NOT NULL"
                }
                if (request.inputStr != null) {
                    wheres += " ${FInputStr} = ?"
                    args += { setString(it, request.inputStr) }
                }
                val inputInt = request.inputInt
                if (inputInt != null) {
                    wheres += " ${FInputInt} = ?"
                    args += { setInt(it, inputInt) }
                }
                append(wheres.joinToString(" AND"))
            }
            append(_andRunning(request.running, FEndDate, hasWhere))

            if (limit > 0)
                append(" LIMIT $limit")

            if (offset > 0)
                append(" OFFSET $offset")
        }

        _ds.connection.use { con ->
            con.prepareStatement(select).use { stmt ->
                args.forEachIndexed { index, function -> stmt.function(index + 1) }

                val list = stmt.executeQuery().toMappedSequence { it._getInstance(_gson) }.toList()

                val token = if (list.size == limit)
                    Base64.getEncoder().encodeToString((offset + limit).toString().toByteArray())
                else
                    null

                list to token
            }
        }
    }

    override fun getStatesInfoOf(instanceId: InstanceId, running: RunningState): List<State.Info> {
        with(StateSQL) {
            _ds.connection.use { con ->
                con.prepareStatement("SELECT ${infoFields} FROM `${Table}` WHERE `${FInstanceId}` = ?" + _andRunning(running, FEndDate) + " ORDER BY ${FStartDate}").use {
                    it.setString(1, instanceId.id)
                    return it.executeQuery().toMappedSequence { it._getStateInfo() }.toList()
                }
            }
        }
    }

    override fun getRecursiveStatesInfoOfRoot(rootInstanceId: InstanceId, running: RunningState): List<State.Info> {
        with(StateSQL) {
            _ds.connection.use { con ->
                con.prepareStatement("SELECT ${infoFields} FROM `${Table}` WHERE `${FRootInstanceId}` = ?" + _andRunning(running, FEndDate) + " ORDER BY ${FStartDate}").use {
                    it.setString(1, rootInstanceId.id)
                    return it.executeQuery().toMappedSequence { it._getStateInfo() }.toList()
                }
            }
        }
    }

    override fun getStatesOf(instanceId: InstanceId, running: RunningState): List<State<*>> {
        with(StateSQL) {
            _ds.connection.use { con ->
                con.prepareStatement("SELECT * FROM `${Table}` WHERE `${FInstanceId}` = ?" + _andRunning(running, FEndDate) + " ORDER BY ${FStartDate}").use {
                    it.setString(1, instanceId.id)
                    return it.executeQuery().toMappedSequence { State(it._getStateInfo(), it._getStateInput(_gson)) }.toList()
                }
            }
        }
    }

    override fun getRecursiveStatesOfRoot(rootInstanceId: InstanceId, running: RunningState): List<State<*>> {
        with(StateSQL) {
            _ds.connection.use { con ->
                con.prepareStatement("SELECT * FROM `${Table}` WHERE `${FRootInstanceId}` = ?" + _andRunning(running, FEndDate) + " ORDER BY ${FStartDate}").use {
                    it.setString(1, rootInstanceId.id)
                    return it.executeQuery().toMappedSequence { State(it._getStateInfo(), it._getStateInput(_gson)) }.toList()
                }
            }
        }
    }

    override fun getInstanceOf(parentStateId: StateId): Instance<*>? = with(InstanceSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT * FROM `${Table}` WHERE `${FParentStateId}` = ?").use {
                it.setString(1, parentStateId.id)
                it.executeQuery().toMappedSequence { it._getInstance(_gson) }.firstOrNull()
            }
        }
    }

    override fun hasLinkFrom(stateId: StateId, linkId: Link.Id?): Boolean = with(PastLinkSQL) {
        _ds.connection.use { con ->
            val select = buildString {
                append("SELECT ${FNextStateId} FROM `${Table}` WHERE `${FPastStateId}` = ?")
                if (linkId != null)
                    append(" AND `${FLinkType}` = ? AND `${FLinkName}` = ?")
                append(" LIMIT 1")
            }
            con.prepareStatement(select).use {
                it.setString(1, stateId.id)
                if (linkId != null) {
                    it.setString(2, linkId.type.toString())
                    it.setString(3, linkId.name)
                }
                it.executeQuery().next()
            }
        }
    }

    override fun getLinksFrom(stateId: StateId): Map<PastLink.Info, State.Info> = with(PastLinkSQL) {
        _ds.connection.use { con ->
            val select = buildString {
                append("SELECT ${infoFields}, ${StateSQL.infoFields} from `${Table}`")
                append(" INNER JOIN `${StateSQL.Table}` ON `${FNextStateId}` = `${StateSQL.FId}`")
                append(" WHERE `${FPastStateId}` = ?")
            }
            con.prepareStatement(select).use {
                it.setString(1, stateId.id)
                it.executeQuery().toMappedSequence { it._getPastLinkInfo() to it._getStateInfo() }.toMap()
            }
        }
    }

    override fun getStateIdsLinkedTo(stateId: StateId): List<StateId> = with(PastLinkSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT ${FPastStateId} from `${Table}` WHERE `${FNextStateId}` = ?").use {
                it.setString(1, stateId.id)
                it.executeQuery().toMappedSequence { StateId(it.getString(FPastStateId)) }.toList()
            }
        }
    }

    override fun getLinksTo(stateId: StateId): Map<State.Info, PastLink.Info> = with(PastLinkSQL) {
        _ds.connection.use { con ->
            val select = buildString {
                append("SELECT ${infoFields}, ${StateSQL.infoFields} from `${Table}`")
                append(" INNER JOIN `${StateSQL.Table}` ON `${FPastStateId}` = `${StateSQL.FId}`")
                append(" WHERE `${FNextStateId}` = ?")
            }
            con.prepareStatement(select).use {
                it.setString(1, stateId.id)
                it.executeQuery().toMappedSequence { it._getStateInfo() to it._getPastLinkInfo() }.toMap()
            }
        }
    }

    override fun getLinkOutput(id: PastLink.Id): Any? = with(PastLinkSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT ${outputFields} FROM `${Table}` WHERE `${FPastStateId}` = ? AND ${FNextStateId} = ?").use {
                it.setString(1, id.pastStateId.id)
                it.setString(2, id.nextStateId.id)
                it.executeQuery().toMappedSequence { it._getPastLinkOutput(_gson) }.firstOrNull()
            }
        }
    }

    override fun lockRunningInstanceRootTree(rootInstanceId: InstanceId, instanceId: InstanceId): KPPersistence.LockInstanceResult? {
        val con = _ds.connection
        con.autoCommit = false

        var rrc = true

        try {
            con.prepareStatement(with(InstanceSQL) { "SELECT * FROM `${Table}` WHERE `${FRootInstanceId}` = ? FOR UPDATE" }).use {
                it.setString(1, rootInstanceId.id)
                it.executeQuery().toMappedSequence {}.toList() // Force the read of all selected items
            }

            con.prepareStatement(with(StateSQL) { "SELECT * FROM `${Table}` WHERE `${FRootInstanceId}` = ? FOR UPDATE" }).use {
                it.setString(1, rootInstanceId.id)
                it.executeQuery().toMappedSequence {}.toList() // Force the read of all selected items
            }

            val hierarchy = _getInstanceHierarchy(con, instanceId)
            @Suppress("UNUSED_VALUE")
            if (hierarchy.isEmpty()) {
                rrc = false
                con.rollbackResetClose(null)
                return null
            }

            return KPPersistence.LockInstanceResult(hierarchy, SQLTransaction(con))
        }
        catch (ex: Throwable) {
            if (rrc)
                con.rollbackResetClose(ex)
            throw ex
        }
    }

    override fun getRootInstanceList(rootInstanceId: InstanceId, running: RunningState) = with(InstanceSQL) {
        _ds.connection.use { con ->
            con.prepareStatement("SELECT ${infoFields} FROM `${Table}` WHERE `${FRootInstanceId}` = ?" + _andRunning(running, FEndDate)).use {
                it.setString(1, rootInstanceId.id)
                it.executeQuery().toMappedSequence { it._getInstanceInfo() }.toList() // Force the read of all selected items
            }
        }
    }

    private inner class SQLTransaction(private val _con: Connection) : KPPersistence.Transaction {

        private var _commited = false

        override fun putStatesAndLinks(states: List<State<*>>, links: List<PastLink>) {
            _con.prepareStatement(StateSQL.SQLInsert).use {
                for (state in states) {
                    it._insert(state, _gson)
                    it.executeUpdate()
                    it.clearParameters()
                }
            }

            _con.prepareStatement(PastLinkSQL.SQLInsert).use {
                for (link in links) {
                    it._insert(link, _gson)
                    it.executeUpdate()
                    it.clearParameters()
                }
            }
        }

        override fun updateStatesInfoDate(states: List<State.Info>) {
            with(StateSQL) {
                _con.prepareStatement("UPDATE `${Table}` SET `${FEndDate}` = ? WHERE `${FId}` = ?").use {
                    for (state in states) {
                        val endDate = state.endDate
                        it.setTimestamp(1, if (endDate != null) Timestamp(endDate.time) else null)
                        it.setString(2, state.id.id)
                        it.executeUpdate()
                        it.clearParameters()
                    }
                }
            }
        }

        override fun updateInstancesInfoDate(instances: List<Instance.Info>) {
            with(InstanceSQL) {
                _con.prepareStatement("UPDATE `${Table}` SET `${FEndDate}` = ? WHERE `${FId}` = ?").use {
                    for (instance in instances) {
                        val endDate = instance.endDate
                        it.setTimestamp(1, if (endDate != null) Timestamp(endDate.time) else null)
                        it.setString(2, instance.id.id)
                        it.executeUpdate()
                        it.clearParameters()
                    }
                }
            }
        }

        override fun deleteState(stateId: StateId) {
            with(StateSQL) {
                _con.prepareStatement("DELETE FROM `${Table}` WHERE `${FId}` = ?").use {
                    it.setString(1, stateId.id)
                    it.executeUpdate()
                }
            }
        }

        override fun deleteLink(id: PastLink.Id) {
            with(PastLinkSQL) {
                _con.prepareStatement("DELETE FROM `${Table}` WHERE `${FPastStateId}` = ? AND `${FNextStateId}` = ?").use {
                    it.setString(1, id.pastStateId.id)
                    it.setString(2, id.nextStateId.id)
                    it.executeUpdate()
                }
            }
        }

        override fun deleteInstance(instanceId: InstanceId) {
            with(InstanceSQL) {
                _con.prepareStatement("DELETE FROM `${Table}` WHERE `${FId}` = ?").use {
                    it.setString(1, instanceId.id)
                    it.executeUpdate()
                }
            }
        }

        override fun commit() {
            _con.commit()
            _commited = true
        }

        override fun close() {
            if (!_commited)
                _con.rollbackResetClose(null)
            else
                _con.resetClose(null)
        }

    }

}
