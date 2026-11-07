package com.xalitoria.vote.db.repository

import com.xalitoria.vote.db.tables.VoteCountTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

object VoteCountRepository {
    /** Increments the all-time vote total for [playerName] and returns the new total. Call inside a transaction. */
    fun increment(playerName: String): Long {
        val current = VoteCountTable.selectAll()
            .where { VoteCountTable.playerName eq playerName }
            .singleOrNull()
            ?.get(VoteCountTable.total) ?: 0L
        val newTotal = current + 1
        if (current == 0L) {
            VoteCountTable.insert {
                it[VoteCountTable.playerName] = playerName
                it[VoteCountTable.total] = newTotal
            }
        } else {
            VoteCountTable.update({ VoteCountTable.playerName eq playerName }) {
                it[VoteCountTable.total] = newTotal
            }
        }
        return newTotal
    }
}
