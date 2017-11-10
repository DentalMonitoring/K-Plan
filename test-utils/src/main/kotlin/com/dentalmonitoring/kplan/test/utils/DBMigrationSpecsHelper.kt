package com.dentalmonitoring.kplan.test.utils

import com.google.gson.GsonBuilder
import com.dentalmonitoring.kplan.db.KPSQLPersistence
import com.dentalmonitoring.kplan.workflow.KPPersistence
import org.h2.jdbcx.JdbcConnectionPool
import java.sql.Connection
import java.util.*
import javax.sql.DataSource
import kotlin.concurrent.schedule

class DBMigrationSpecsHelper() {
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

    private val gson = GsonBuilder().setPrettyPrinting().create()

    //  TIPS: to show DB content, evaluate (dynamically) : org.h2.tools.Server.startWebServer(helper.db.connection)
    private val db = JdbcConnectionPool.create("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1", "user", "password")

    fun cleanDB() {
        db.connection.use {
            it.createStatement().execute("""
                -- noinspection SqlNoDataSourceInspectionForFile
                DROP ALL OBJECTS
                """)
        }
    }

    fun persistence(): KPPersistence = KPSQLPersistence(LoggedDataSource(db), gson)

    fun applyMigration(migrationFunc: (Connection) -> (Unit)) = db.connection.use { migrationFunc(it) }

    fun executeSQLStatement(sqlStatement: String) = db.connection.use { it.createStatement().execute(sqlStatement) }
}
