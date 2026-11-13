package com.xalitoria.vote.config

import com.xalitoria.util.config.DatabaseConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VoteConfig(
    val database: DatabaseConfig = DatabaseConfig(
        tablePrefix = "xalivote_",
        sqlite = DatabaseConfig.Sqlite("vote.db"),
        mysql = DatabaseConfig.Mysql(database = "vote", poolSize = 4),
    ),
    val webhook: Webhook = Webhook(),
    val links: List<Link> = [],
    @SerialName("server-links")
    val serverLinks: ServerLinks = ServerLinks(),
    val party: Party = Party(),
    val top: Top = Top(),
    val menu: Menu = Menu(),
    val proxy: Proxy = Proxy(),
) {
    @Serializable
    data class Webhook(
        val enabled: Boolean = false,
        val port: Int = 8192,
        val secret: String = "",
    )

    /** A place where a player can vote. [url] must be an http or https address. */
    @Serializable
    data class Link(
        val name: String,
        val url: String,
        val description: String = "",
    )

    /** Whether the links are also added to the game's own Server Links screen (the pause menu). */
    @Serializable
    data class ServerLinks(
        val enabled: Boolean = false,
        /** Put in front of each link's name there, so the vote links are easy to tell from the server's other links. */
        val prefix: String = "Vote: ",
    )

    /**
     * Every [votes] votes, [commands] run for each player who is online and [once] run once. `{player}` is the player, `{party}`
     * the number of the party and `{votes}` how many votes make one.
     */
    @Serializable
    data class Party(
        val enabled: Boolean = false,
        val votes: Int = 25,
        val commands: List<String> = [],
        val once: List<String> = [],
    )

    @Serializable
    data class Top(
        /** How many places are kept, for the command, the menu and the placeholders. */
        val size: Int = 10,
        /** How often the places are read from the database again, for votes that other servers counted. */
        val refresh: String = "1m",
    )

    @Serializable
    data class Menu(
        val enabled: Boolean = true,
        /** The menu `/vote` opens; the menu of the top voters is [top]. */
        val name: String = "vote",
        val top: String = "votetop",
        @SerialName("write-menus")
        val writeMenus: Boolean = true,
    )

    /**
     * Behind Velocity the plugin on the proxy keeps the settings the servers share (the links and the party) and tells them, and
     * a party that one counts is run by all of them. A server uses what the proxy tells it when [enabled] is on. On the proxy,
     * [enabled] is whether it tells the servers at all.
     */
    @Serializable
    data class Proxy(
        val enabled: Boolean = true,
    )
}
