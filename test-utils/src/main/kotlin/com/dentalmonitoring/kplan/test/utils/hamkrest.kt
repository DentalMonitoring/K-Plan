package com.dentalmonitoring.kplan.test.utils

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.equalTo
import java.sql.ResultSet
import java.util.*

// === LANG ===

fun Boolean.isTrue() = this
val isTrue = Matcher(Boolean::isTrue)

fun Boolean.isFalse() = !this
val isFalse = Matcher(Boolean::isFalse)

fun Date.timeEquals(other: Date) = time == other.time
fun timeEqualTo(date: Date) = com.natpryce.hamkrest.has(Date::getTime, equalTo(date.time))

data class Feature<T, R>(val name: String, val getter: (T) -> R)

fun <T, R> has(feature: Feature<T, R>, matcher: Matcher<R>) = com.natpryce.hamkrest.has(feature.name, feature.getter, matcher)

// === COLLECTION ===

fun Collection<*>.hasOneElement() = size == 1

fun <T> oneElement(featureMatcher: Matcher<T>) = Matcher(Collection<*>::hasOneElement) and com.natpryce.hamkrest.has("its first element", { c: Collection<T> -> c.first() }, featureMatcher)

fun <T> elements(vararg featureMatchers: Matcher<T>): Matcher<List<T>> {
    return featureMatchers
            .mapIndexed { i, fm -> com.natpryce.hamkrest.has("element $i", { c: List<T> -> c[i] }, fm) }
            .reduce { l, r -> l and r }
            .and(com.natpryce.hamkrest.has("size", List<T>::size, equalTo(featureMatchers.size)))
}

// === SQL ===

private fun <T> _sqlFeature(name: String, type: String, get: ResultSet.(String) -> T) = Feature<ResultSet, T>("$type field $name", { it.get(name) })

fun stringField(name: String) = _sqlFeature(name, "String", ResultSet::getString)
fun intField(name: String) = _sqlFeature(name, "String", ResultSet::getInt)
fun timestampField(name: String) = _sqlFeature(name, "String", ResultSet::getTimestamp)

// === Kotlin 1.1 ===
// TODO: should be removed once Kotlin 1.1 has re-implemented its reflection on built-in types.

inline fun <reified T : Any> cast(typeName: String, downcastMatcher: Matcher<T>): Matcher<Any> {
    return object : Matcher<Any> {
        override fun invoke(actual: Any) = when (actual) {
            is T -> downcastMatcher(actual)
            else -> MatchResult.Mismatch("had type ${actual.javaClass.kotlin.qualifiedName}")
        }

        override val description: String get() = "has type $typeName" + " & " + downcastMatcher.description
    }
}
