package com.xalitoria.vote

import com.xalitoria.vote.config.RewardEntry

/**
 * Returns the commands to dispatch for a vote with the given [voteNumber] (1-indexed all-time total).
 * An entry fires if voteNumber >= after && (voteNumber - after) % every == 0.
 * An entry is suppressed if any entry it lists in [RewardEntry.exclude] also fires.
 */
fun resolveCommands(voteNumber: Long, rewards: Map<String, RewardEntry>): List<String> {
    val firing = rewards.entries
        .filter {
            voteNumber >= it.value.after &&
            (voteNumber - it.value.after) % it.value.every == 0L
        }
        .map { it.key }
        .toSet()
    return firing
        .filter { rewards[it]?.exclude.orEmpty().none(firing::contains) }
        .flatMap { rewards[it]?.commands ?: emptyList() }
}

fun defaultRewardsConfig(): Map<String, RewardEntry> = mapOf(
    "basic-vote" to RewardEntry(every = 1, commands = emptyList(), exclude = listOf("milestone")),
    "milestone" to RewardEntry(after = 10L, every = 5, commands = emptyList()),
)
