package com.dentalmonitoring.kplan.workflow.task.utils

import com.dentalmonitoring.kplan.model.Instance

data class Inputs<out TI : Any, out WI : Any>(val state: TI, val instance: Instance.Input<WI>)
