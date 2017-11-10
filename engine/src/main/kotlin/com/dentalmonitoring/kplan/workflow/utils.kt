package com.dentalmonitoring.kplan.workflow

internal val String?.uuidStart: String? get() = this?.split('-')?.get(0)

