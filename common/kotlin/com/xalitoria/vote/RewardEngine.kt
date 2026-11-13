package com.xalitoria.vote

import com.xalitoria.vote.config.RewardEntry

/**
 * Returns the commands to dispatch for a vote with the given [voteNumber] (1-indexed all-time total).
 * An entry fires if voteNumber >= after && (voteNumber - after) % every == 0 (an `every` below 1 counts as 1).
 * An entry is suppressed if any entry it lists in [RewardEntry.exclude] also fires.
 *
 * The vouchers of a firing entry are turned into commands by [voucher], which is given the id and the amount and answers null
 * for no command (when there is nothing to give them with). Without it they are left out.
 */
fun resolveCommands(
    voteNumber: Long,
    rewards: Map<String, RewardEntry>,
    voucher: ((String, Int) -> String?)? = null,
): List<String> {
    val firing = rewards.entries
        .filter {
            voteNumber >= it.value.after &&
            (voteNumber - it.value.after) % it.value.every.coerceAtLeast(1) == 0L
        }
        .map { it.key }
        .toSet()
    return firing
        .filter { rewards[it]?.exclude.orEmpty().none(firing::contains) }
        .flatMap { key ->
            val entry = rewards[key] ?: return@flatMap listOf()
            val vouchers = if (voucher == null) listOf() else entry.vouchers.mapNotNull { if (it.value > 0) voucher(it.key, it.value) else null }
            entry.commands + vouchers
        }
}

fun defaultRewardsConfig(): Map<String, RewardEntry> = mapOf(
    "basic-vote" to RewardEntry(every = 1, exclude = ["milestone"]),
    "milestone" to RewardEntry(after = 10L, every = 5),
)
