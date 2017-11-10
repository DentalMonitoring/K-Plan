package com.dentalmonitoring.kplan.test.utils

import com.google.gson.GsonBuilder
import com.dentalmonitoring.kplan.KPlan
import com.dentalmonitoring.kplan.db.KPSQLPersistence
import com.dentalmonitoring.kplan.db.migration.V2016_08_03_01__KPlan_initialization
import com.dentalmonitoring.kplan.db.migration.V2017_07_12_01__KPlan_add_constraint
import com.dentalmonitoring.kplan.db.migration.V2017_10_02_01__KPlan_add_parentInstanceId_for_optimizations
import com.dentalmonitoring.kplan.impl.KplanImpl
import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.workflow.KPPersistence
import org.h2.jdbcx.JdbcConnectionPool
import org.jetbrains.spek.api.dsl.SpecBody
import java.sql.Connection
import java.util.*
import javax.sql.DataSource
import kotlin.concurrent.schedule

class KPlanSpecsHelper(body: SpecBody) {

    private class LoggedConnection(val con: Connection) : Connection by con {

        private val timer = Timer()

        init {
            val exc = Exception()
            timer.schedule(2000) {
                exc.printStackTrace()
            }
        }

        override fun close() {
            timer.cancel()
            con.close()
        }
    }

    private class LoggedDataSource(val ds: DataSource) : DataSource by ds {
        override fun getConnection(): Connection? {
            return LoggedConnection(ds.connection)
        }
    }

    val gson = GsonBuilder().setPrettyPrinting().create()

    //  TIPS: to show DB content, evaluate (dynamically) : org.h2.tools.Server.startWebServer(helper.db.connection)
    val db = JdbcConnectionPool.create("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1", "user", "password")

    fun newPersistance(): KPPersistence = KPSQLPersistence(LoggedDataSource(db), gson)

    fun newKplan() = KplanImpl(newPersistance(), KPLocalEventBus())

    init {
        body.beforeEachTest {
            db.connection.use {
                it.createStatement().execute("""
                -- noinspection SqlNoDataSourceInspectionForFile
                DROP ALL OBJECTS
                """)
                V2016_08_03_01__KPlan_initialization.migrate(it)
                V2017_07_12_01__KPlan_add_constraint.migrate(it)
                V2017_10_02_01__KPlan_add_parentInstanceId_for_optimizations.migrate(it)
            }
        }
    }
}

fun KPlan.getRecursiveRunningBusinessStates(instanceId: InstanceId) = getRecursiveStatesOfRoot(instanceId, businessOnly = true).filter { !it.info.isPast }
fun KPlan.getRunningBusinessStates(instanceId: InstanceId) = getStatesOf(instanceId, businessOnly = true).filter { !it.info.isPast }
fun KPlan.getRecursiveRunningStates(instanceId: InstanceId) = getRecursiveStatesOfRoot(instanceId, businessOnly = false).filter { !it.info.isPast }
fun KPlan.getRunningStates(instanceId: InstanceId) = getStatesOf(instanceId, businessOnly = false).filter { !it.info.isPast }
fun KPlan.getPastStates(instanceId: InstanceId) = getStatesOf(instanceId).filter { it.info.isPast }
