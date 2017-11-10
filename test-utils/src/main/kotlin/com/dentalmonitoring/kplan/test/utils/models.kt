package com.dentalmonitoring.kplan.test.utils

import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.workflow.KPEventBus

val State<*>.taskName get() = info.taskName
val KPEventBus.Event.State.stateTaskName get() = state.taskName

val Instance<*>.workflowName get() = info.workflowName
val KPEventBus.Event.Instance.instanceWorkflowName get() = instance.workflowName
