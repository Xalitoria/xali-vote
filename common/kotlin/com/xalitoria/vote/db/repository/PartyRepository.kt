package com.xalitoria.vote.db.repository

import com.xalitoria.vote.db.tables.PartyTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

object PartyRepository {
    /** What a vote did to the party: how far the count is now, how many parties it set off (usually 0 or 1) and the number of the last. */
    data class Counted(val progress: Long, val started: Int, val lastNumber: Long)

    data class Status(val progress: Long, val parties: Long)

    private const val ROW = 1

    /**
     * Counts one vote toward a party of [votes] votes. A party is claimed by lowering the count, in one guarded statement
     * (`WHERE progress >= votes`), so that of several servers counting at the same time exactly one starts each party. Call inside a
     * transaction.
     */
    fun record(votes: Int): Counted {
        val every = votes.coerceAtLeast(1)
        ensure()
        PartyTable.update({ PartyTable.id eq ROW }) { it[progress] = progress + 1 }
        var started = 0
        // More than one only when the setting was lowered below what is already counted; it is all caught up, but not endlessly.
        while (started < MAX_CATCH_UP) {
            val claimed = PartyTable.update({ (PartyTable.id eq ROW) and (PartyTable.progress greaterEq every.toLong()) }) {
                it[progress] = progress - every.toLong()
                it[parties] = parties + 1
            }
            if (claimed == 0) break
            started++
        }
        val row = PartyTable.selectAll().where { PartyTable.id eq ROW }.single()
        return Counted(row[PartyTable.progress], started, row[PartyTable.parties])
    }

    fun status(): Status {
        ensure()
        val row = PartyTable.selectAll().where { PartyTable.id eq ROW }.single()
        return Status(row[PartyTable.progress], row[PartyTable.parties])
    }

    private fun ensure() {
        if (PartyTable.selectAll().where { PartyTable.id eq ROW }.empty()) PartyTable.insertIgnore { it[id] = ROW }
    }

    private const val MAX_CATCH_UP = 20
}
