package com.dentalmonitoring.kplan.model

data class Link(
        val id: Id,
        val multi: Boolean = false
) {

    init {
        if (multi && id.type != Type.SIGNAL)
            throw IllegalArgumentException("Only signals can have multi = true")
    }

    enum class Type {
        FLOW,
        SIGNAL,
        INTERRUPTION
    }

    data class Id(
            val type: Type,
            val name: String
    )

}
