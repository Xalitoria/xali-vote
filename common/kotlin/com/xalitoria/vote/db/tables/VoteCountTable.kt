package com.xalitoria.vote.db.tables

import com.xalitoria.vote.db.DatabaseManager
import org.jetbrains.exposed.v1.core.Table

/** All-time vote count per player — used to determine milestone thresholds. */
object VoteCountTable : Table(DatabaseManager.tableName("vote_counts")) {
    val playerName = varchar("player_name", 64)
    val total = long("total").default(0)

    override val primaryKey = PrimaryKey(playerName)
}
