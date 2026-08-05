package com.mcclabs.mook.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Interaction(
    val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val type: String = "", // "like" or "pass"
    val timestamp: Long = 0L
)
