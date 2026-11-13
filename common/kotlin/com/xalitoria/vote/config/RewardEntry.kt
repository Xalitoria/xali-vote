package com.xalitoria.vote.config

import kotlinx.serialization.Serializable

@Serializable
data class RewardEntry(
    val every: Int = 1,
    val after: Long = 1L,
    val commands: List<String> = [],
    /** Vouchers of XaliRedeem to give, the id of each with how many. Given by the Paper plugin, and only when XaliRedeem is installed. */
    val vouchers: Map<String, Int> = mapOf(),
    val exclude: List<String> = [],
)
