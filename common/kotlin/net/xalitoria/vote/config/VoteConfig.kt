package net.xalitoria.vote.config

import kotlinx.serialization.Serializable

@Serializable
data class VoteConfig(
    val database: Database = Database(),
    val webhook: Webhook = Webhook(),
) {
    @Serializable
    data class Database(
        val type: String = "sqlite",
        val tablePrefix: String = "xalivote_",
        val serverName: String = "default",
        val sqlite: Sqlite = Sqlite(),
        val mysql: Mysql = Mysql(),
    )

    @Serializable
    data class Sqlite(val filename: String = "vote.db")

    @Serializable
    data class Mysql(
        val host: String = "localhost",
        val port: Int = 3306,
        val database: String = "vote",
        val username: String = "root",
        val password: String = "",
        val useSsl: Boolean = false,
        val poolSize: Int = 4,
    )

    @Serializable
    data class Webhook(
        val enabled: Boolean = false,
        val port: Int = 8192,
        val secret: String = "",
    )
}
