package com.xalitoria.vote.db.repository

import com.xalitoria.vote.db.tables.VoteCountTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

object VoteCountRepository {
    /**
     * Increments the all-time vote total for [playerName] and returns the new total. The total is raised in the database,
     * not read and written back, so two votes at once (or from two servers) get two numbers. Call inside a transaction.
     */
    fun increment(playerName: String): Long {
        val raise = { VoteCountTable.update({ VoteCountTable.playerName eq playerName }) { it[total] = total + 1 } }
        if (raise() == 0) {
            val inserted = VoteCountTable.insertIgnore {
                it[VoteCountTable.playerName] = playerName
                it[total] = 1
            }
            // Another vote made the row in the meantime.
            if (inserted.insertedCount == 0) raise()
        }
        return VoteCountTable.selectAll().where { VoteCountTable.playerName eq playerName }.single()[VoteCountTable.total]
    }
}
