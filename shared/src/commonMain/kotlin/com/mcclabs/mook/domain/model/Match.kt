package com.mcclabs.mook.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Match(
    val id: String = "",
    val users: List<String> = emptyList(),
    val timestamp: Long = 0L
)
