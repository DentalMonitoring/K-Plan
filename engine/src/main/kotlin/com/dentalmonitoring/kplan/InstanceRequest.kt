package com.dentalmonitoring.kplan

import com.dentalmonitoring.kplan.model.RunningState
import com.dentalmonitoring.kplan.model.RunningState.ONLY_RUNNING

data class InstanceRequest(
        val wfName: String? = null,
        val root: Boolean? = true,
        val running: RunningState = ONLY_RUNNING,
        val inputStr: String? = null,
        val inputInt: Int? = null
)