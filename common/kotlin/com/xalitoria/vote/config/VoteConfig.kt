package com.xalitoria.vote.config

import com.xalitoria.util.config.DatabaseConfig
import kotlinx.serialization.Serializable

@Serializable
data class VoteConfig(
    val database: DatabaseConfig = DatabaseConfig(
        tablePrefix = "xalivote_",
        sqlite = DatabaseConfig.Sqlite("vote.db"),
        mysql = DatabaseConfig.Mysql(database = "vote", poolSize = 4),
    ),
    val webhook: Webhook = Webhook(),
) {
    @Serializable
    data class Webhook(
        val enabled: Boolean = false,
        val port: Int = 8192,
        val secret: String = "",
    )
}
