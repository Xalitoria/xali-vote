package net.xalitoria.vote.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.xalitoria.vote.config.VoteConfig
import net.xalitoria.vote.db.tables.VoteCountTable
import net.xalitoria.vote.db.tables.VoteTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

object DatabaseManager {
    lateinit var dataSource: HikariDataSource
        private set
    lateinit var database: Database
        private set

    var tablePrefix = "xalivote_"
        private set

    fun tableName(name: String): String = tablePrefix + name

    fun connect(dataFolder: File, config: VoteConfig.Database) {
        tablePrefix = config.tablePrefix

        val hikari = HikariConfig()
        hikari.poolName = "XaliVote-DB"

        when (config.type.lowercase()) {
            "mysql", "mariadb" -> {
                val m = config.mysql
                hikari.jdbcUrl = "jdbc:mysql://${m.host}:${m.port}/${m.database}?useSSL=${m.useSsl}"
                hikari.username = m.username
                hikari.password = m.password
                hikari.driverClassName = "com.mysql.cj.jdbc.Driver"
                hikari.maximumPoolSize = m.poolSize.coerceAtLeast(1)
            }
            else -> {
                dataFolder.mkdirs()
                val dbFile = dataFolder.resolve(config.sqlite.filename)
                hikari.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                hikari.driverClassName = "org.sqlite.JDBC"
                hikari.maximumPoolSize = 1
            }
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
