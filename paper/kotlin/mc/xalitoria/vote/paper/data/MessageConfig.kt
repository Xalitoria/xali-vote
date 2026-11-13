package mc.xalitoria.vote.paper.data

import kotlinx.serialization.Serializable

/** `messages.yml`: what the commands say. Colors, hex and MiniMessage work; `{name}`, `{url}` and the like are filled in. */
@Serializable
data class MessageConfig(
    val linksHeader: String = "&6Vote for the server, every site counts:",
    val linkLine: String = "&8» &a{name}",
    val linkDescription: String = " &8- &7{description}",
    val linkHover: String = "&7Click to open &f{url}",
    val noLinks: String = "&cThere is nowhere to vote yet.",
    val linkOpened: String = "&aOpen it here: &f{url}",
    val topHeader: String = "&6Top voters:",
    val topLine: String = "&e#{position} &f{name} &8- &7{votes} votes",
    val topEmpty: String = "&7Nobody has voted yet.",
    val votes: String = "&f{name} &7has voted &f{votes} &7times.",
    val partyHeader: String = "&6Vote party:",
    val partyProgress: String = "&7{progress} of {votes} votes counted, &f{remaining} &7to go. Parties so far: &f{parties}.",
    val partyOff: String = "&7There is no vote party on this server.",
    val noMenu: String = "&cThere is no menu, so here is the list instead.",
    val notPlayer: String = "&cOnly a player can do that.",
    val unknownPlayer: String = "&c{name} has never voted.",
    val databaseFailed: String = "&cCould not read the votes; see the console.",
    val reloaded: String = "&aReloaded. {links} link(s), party {party}, proxy {proxy}.",
    val reloadFailed: String = "&cThe configuration is wrong, so nothing changed: {error}",
)
