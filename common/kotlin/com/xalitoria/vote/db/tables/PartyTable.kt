package com.xalitoria.vote.db.tables

import com.xalitoria.vote.db.DatabaseManager
import org.jetbrains.exposed.v1.core.Table

/** One row (id 1): the votes counted toward the next vote party, and how many parties have been held. Shared by every server on the database. */
object PartyTable : Table(DatabaseManager.tableName("vote_party")) {
    val id = integer("id")
    val progress = long("progress").default(0)
    val parties = long("parties").default(0)

    override val primaryKey = PrimaryKey(id)
}
