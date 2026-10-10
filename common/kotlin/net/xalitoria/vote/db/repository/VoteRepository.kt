package net.xalitoria.vote.db.repository

import net.xalitoria.vote.db.tables.VoteTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

object VoteRepository {
    /** Queues one pending vote for an offline player. Call inside a transaction. */
    fun save(playerName: String, serviceName: String, address: String, voteNumber: Long) {
        VoteTable.insert {
            it[VoteTable.playerName] = playerName
            it[VoteTable.serviceName] = serviceName
            it[VoteTable.address] = address
            it[VoteTable.timestamp] = System.currentTimeMillis()
            it[VoteTable.voteNumber] = voteNumber
        }
    }

    /**
     * Atomically claims all pending votes for [playerName], ordered oldest-first.
     * Returns the vote numbers for reward resolution. Call inside a transaction.
     */
    fun claimAll(playerName: String): List<Long> = VoteTable.selectAll()
        .where { VoteTable.playerName eq playerName }
        .orderBy(VoteTable.timestamp, SortOrder.ASC)
        .map { it[VoteTable.voteNumber] }
        .takeIf { it.isNotEmpty() }
        ?.also { VoteTable.deleteWhere { VoteTable.playerName eq playerName } }
        .orEmpty()

    fun hasPending(playerName: String): Boolean =
        VoteTable.selectAll().where { VoteTable.playerName eq playerName }.any()
}
