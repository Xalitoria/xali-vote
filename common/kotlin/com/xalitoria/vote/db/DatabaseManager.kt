package com.xalitoria.vote.db

import com.xalitoria.util.config.DatabaseConfig
import com.xalitoria.util.config.databaseType
import com.xalitoria.vote.db.tables.VoteCountTable
import com.xalitoria.vote.db.tables.VoteTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.io.File

object DatabaseManager {
    lateinit var dataSource: HikariDataSource
        private set
    lateinit var database: Database
        private set

    var tablePrefix = "xalivote_"
        private set

    fun tableName(name: String): String = tablePrefix + name

    fun connect(dataFolder: File, config: DatabaseConfig) {
        tablePrefix = DatabaseConfig.checkPrefix(config.tablePrefix)

        val hikari = HikariConfig()
        hikari.poolName = "XaliVote-DB"

        val type = config.databaseType
        if (type.isRemote) {
            val m = config.mysql
            hikari.jdbcUrl = m.jdbcUrl(type)
            hikari.driverClassName = m.driverClass(type)
            hikari.username = m.username
            hikari.password = m.password
            hikari.maximumPoolSize = m.poolSize.coerceAtLeast(1)
        } else {
            dataFolder.mkdirs()
            val dbFile = dataFolder.resolve(config.sqlite.filename)
            hikari.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            hikari.driverClassName = "org.sqlite.JDBC"
            hikari.maximumPoolSize = 1
        }

        dataSource = HikariDataSource(hikari)
        database = Database.connect(dataSource)

        transaction(database) {
            MigrationUtils.statementsRequiredForDatabaseMigration(
                VoteTable, VoteCountTable, withLogs = false,
            ).forEach { exec(it) }
        }
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    suspend fun <T> dbQuery(block: suspend JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db = database, statement = block) }
}
