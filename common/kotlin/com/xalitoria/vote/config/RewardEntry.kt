package com.xalitoria.vote.config

import kotlinx.serialization.Serializable

@Serializable
data class RewardEntry(
    val every: Int = 1,
    val after: Long = 1L,
    val commands: List<String> = [],
    val exclude: List<String> = [],
)
