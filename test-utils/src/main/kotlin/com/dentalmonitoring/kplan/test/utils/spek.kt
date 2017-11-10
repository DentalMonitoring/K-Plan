package com.dentalmonitoring.kplan.test.utils

import org.jetbrains.spek.api.dsl.TestContainer
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.xit
import java.io.Closeable
import java.util.ArrayList

interface ResourcesBody {
    fun onCleanup(block: () -> Unit)
    fun <T : Closeable> T.autoCleanup(): T = apply { onCleanup { close() } }
    fun <T : AutoCloseable> T.autoCleanup(): T = apply { onCleanup { close() } } // Remove in JDK 6
}

private class ResourcesBodyImpl : ResourcesBody {
    val cleanups = ArrayList<() -> Unit>()
    override fun onCleanup(block: () -> Unit) {
        cleanups += block
    }
}

fun R(block: ResourcesBody.() -> Unit) {
    val body = ResourcesBodyImpl()

    try {
        body.block()
    }
    finally {
        var cleanupException: Exception? = null
        for (cleanup in body.cleanups)
            try {
                cleanup.invoke()
            }
            catch (e: Exception) {
                if (cleanupException == null)
                    cleanupException = e
                else
                    cleanupException.addSuppressed(e) // Remove in JDK 6
            }
        if (cleanupException != null)
            throw cleanupException
    }
}

fun TestContainer.itR(description: String, assertions: ResourcesBody.() -> Unit) = it(description) { com.dentalmonitoring.kplan.test.utils.R(assertions) }
fun TestContainer.xitR(description: String, assertions: ResourcesBody.() -> Unit) = xit(description) { com.dentalmonitoring.kplan.test.utils.R(assertions) }
//fun Dsl.fitR(description: String, assertions: ResourcesBody.() -> Unit) = fit(description) { R(assertions) }
