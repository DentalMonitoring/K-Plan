package com.dentalmonitoring.kplan.test.utils

import com.dentalmonitoring.kplan.KPlan
import com.dentalmonitoring.kplan.model.StateId
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.Workflow
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has

fun moveWithUnit(taskName: String, kPlan: KPlan, instance: Instance<*>): StateId {
    val state = kPlan.getRunningBusinessStates(instance.info.id).first { it.info.taskName == taskName }
    kPlan.getMove(state.info).flow(Unit)
    return state.info.id
}

fun moveWithString(taskName: String, kPlan: KPlan, instance: Instance<*>): StateId {
    val state = kPlan.getRunningBusinessStates(instance.info.id).first { it.info.taskName == taskName }
    kPlan.getMove(state.info).flow(getOutTaskName(taskName))
    return state.info.id
}

fun signal(taskName: String, kPlan: KPlan, instance: Instance<*>): StateId {
    val state = kPlan.getRunningBusinessStates(instance.info.id).first { it.info.taskName == taskName }
    kPlan.getMove(state.info).signal(genericSignal, genericSignalInput)
    return state.info.id
}

fun interrupt(taskName: String, kPlan: KPlan, instance: Instance<*>): StateId {
    val state = kPlan.getRunningBusinessStates(instance.info.id).first { it.info.taskName == taskName }
    kPlan.getMove(state.info).interrupt(genericInterrupt, genericInterruptInput)
    return state.info.id
}

fun hasTask(taskName: String) = has(State<*>::info, has(State.Info::taskName, equalTo(taskName)))

fun hasTaskMap(taskMap: Map<String, String>) = has(State<*>::input, equalTo(taskMap as Any))

fun getOutTaskName(taskName: String) = "Out-$taskName"
const val genericSubWorkflowName = "sub-workflow"
const val genericSignal = "signal"
const val genericSignalInput = "signal-input"
const val genericParallelName = "parallel"
const val genericInterrupt = "interrupt"
const val genericInterruptInput = "interrupt-input"

const val simpleManualLinearWorkflowName = "simple-manual-linear"

fun getSimpleManualLinearWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(simpleManualLinearWorkflowName) {
        val A = businessTask<Unit, String>("A")
        val B = businessTask<String, String>("B")
        val C = businessTask<String, Unit>("C")
        val Z = normalEndTask<Unit>("Z")

        start with A then B then C then Z
    }
}

const val simpleAutomaticLinearWorkflowName = "simple-automatic-linear"

fun getSimpleAutomaticLinearWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(simpleAutomaticLinearWorkflowName) {
        val A = businessTask<Unit, String>("A") { it.flow("string") }
        val B = businessTask<String, String>("B")
        val C = businessTask<String, Unit>("C") { it.flow(Unit) }
        val Z = normalEndTask<Unit>("Z")

        start with A then B then C then Z
    }
}

const val simpleParallelWorkflowName = "simple-parallel"

fun getSimpleParallelWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(simpleParallelWorkflowName) {
        val A = businessTask<Unit, String>("A")
        val B = businessTask<String, String>("B")
        val C = businessTask<String, String>("C")
        val D = businessTask<Map<String, Any>, Unit>("D")
        val Z = normalEndTask<Unit>("Z")

        start with A then parallel(genericParallelName, B, C) then D then Z
    }
}

const val parallelTwoLayerWorkflowName = "parallel-two-layer"

fun getParallelTwoLayerWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(parallelTwoLayerWorkflowName) {
        val A = businessTask<Unit, Unit>("A")
        val B = workflowTaskFromInstance<Unit>("B", kPlan.new(genericSubWorkflowName) {
            val C = businessTask<Unit, Unit>("C")
            val Z = normalEndTask<Unit>("Z")

            start with C then Z
        })
        val Z = normalEndTask<Unit>("Z")

        start with sparallel(genericParallelName, A, B) then Z
    }
}

const val simpleInterruptWorkflowName = "simple-interrupt"

fun getSimpleInterruptWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(simpleInterruptWorkflowName) {
        val A = businessTask<Unit, Unit>("A")
        val B = businessTask<String, Unit>("B")
        val Z = normalEndTask<Unit>("Z")

        start with A then Z
        A on interruption(genericInterrupt) then B then Z
    }
}

const val simpleSignalWorkflowName = "simple-signal"

fun getSimpleSignalWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(simpleSignalWorkflowName) {
        val A = businessTask<Unit, Unit>("A")
        val B = businessTask<String, Unit>("B")
        val Z = normalEndTask<Unit>("Z")

        start with A then Z
        A on signal(genericSignal) then B
    }
}

const val signalAndBarrierWorkflowName = "signal-and-barrier"

fun getSignalAndBarrierWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(signalAndBarrierWorkflowName) {
        val A = businessTask<Unit, Unit>("A")
        val B = businessTask<String, Unit>("B")
        val C = businessTask<Map<String, Any>, Unit>("C")
        val X = barrierGate("X")
        val Z = normalEndTask<Unit>("Z")

        start with A then X then C then Z
        A on signal(genericSignal) then B then X
    }
}

const val twoLayerWorkflowName = "two-layer"

fun getTwoLayerWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(twoLayerWorkflowName) {
        val A = businessTask<Unit, Unit>("A")
        val B = businessTask<Unit, Unit>("B")
        val C = workflowTaskFromState<Unit, Unit>("C", kPlan.new(genericSubWorkflowName) {
            val D = businessTask<Unit, Unit>("D")
            val E = businessTask<Unit, Unit>("E")
            val Z = normalEndTask<Unit>("Z")

            start with D then E then Z
        })
        val F = businessTask<Unit, Unit>("F")
        val Z = normalEndTask<Unit>("Z")

        start with A then B then C then F then Z
    }
}

const val twoLayerInterruptWorkflowName = "two-layer-interrupt"

fun getTwoLayerInterruptWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(twoLayerInterruptWorkflowName) {
        val A = businessTask<Unit, Unit>("A")
        val B = businessTask<Unit, Unit>("B")
        val C = workflowTaskFromInstance<Unit>(genericSubWorkflowName, kPlan.new(genericSubWorkflowName) {
            val D = businessTask<Unit, Unit>("D")
            val E = businessTask<Unit, Unit>("E")
            val Z = normalEndTask<Unit>("Z")

            start with D then E then Z
        })
        val F = businessTask<Unit, Unit>("F")
        val Z = normalEndTask<Unit>("Z")

        start with A then B then C then Z
        C on interruption(genericInterrupt) then F then Z
    }
}

const val complexWorkflowName = "complex"

fun getComplextWorkflow(kPlan: KPlan): Workflow<Unit, Unit> {
    return kPlan.new<Unit, Unit>(complexWorkflowName) {
        val A = businessTask<Unit, Unit>("A") { it.flow(Unit) }
        val B = businessTask<Unit, Unit>("B")
        val C = workflowTaskFromInstance("C", kPlan.new<Unit, Unit>(genericSubWorkflowName) {
            val D = businessTask<Unit, Unit>("D") { it.flow(Unit) }
            val E = businessTask<Unit, Unit>("E")
            val Z = normalEndTask<Unit>("Z")

            start with D then E then Z
        })
        val Z = normalEndTask<Unit>("Z")

        start with A then sparallel(genericParallelName, B, C) then Z
    }
}