package com.xalitoria.vote.db.tables

import com.xalitoria.vote.db.DatabaseManager
import org.jetbrains.exposed.v1.core.Table

/** Pending votes for offline players — deleted when dispatched on join. */
object VoteTable : Table(DatabaseManager.tableName("votes")) {
    val id = long("id").autoIncrement()
    val playerName = varchar("player_name", 64)
    val serviceName = varchar("service_name", 64)
    val address = varchar("address", 128)
    val timestamp = long("timestamp")
    val voteNumber = long("vote_number")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, playerName, timestamp)
    }
}
