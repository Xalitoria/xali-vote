package mc.xalitoria.vote.paper.data

import kotlinx.serialization.Serializable

/** What every heading starts with: the same colors in every Xali plugin. */
private const val HEAD = "{#a0a>}&lXALI{#fff<}&f&lVOTE &7>"

/** `messages.yml`: what the commands say. Colors, hex and MiniMessage work; `{name}`, `{url}` and the like are filled in. */
@Serializable
data class MessageConfig(
    val linksHeader: String = "$HEAD &dVote for the server, every site counts:",
    val linkLine: String = "&7» &d{name}",
    val linkDescription: String = " &7- {description}",
    val linkHover: String = "&7Click to open &f{url}",
    val noLinks: String = "There is nowhere to vote yet.",
    val linkOpened: String = "&aOpen it here: &f{url}",
    val topHeader: String = "$HEAD &dTop voters",
    val topLine: String = "&d#{position} &f{name} &7- {votes|number} {votes|plural:vote,votes}",
    val topEmpty: String = "Nobody has voted yet.",
    val votes: String = "&f{name} &7has voted &f{votes|number} &7{votes|plural:time,times}.",
    val partyHeader: String = "$HEAD &dVote party",
    val partyProgress: String = "&7{progress|number} of {votes|number} votes counted, &f{remaining|number} &7to go. Parties so far: &f{parties|number}.",
    val partyOff: String = "There is no vote party on this server.",
    val noMenu: String = "There is no menu, so here is the list instead.",
    val notPlayer: String = "You must be a player to use this command.",
    val unknownPlayer: String = "{name} has never voted.",
    val databaseFailed: String = "Could not read the votes; see the console.",
    val reloaded: String = "Configuration reloaded: {links} link(s), party {party}, proxy {proxy}.",
    val reloadFailed: String = "The configuration was not reloaded: {error}",
)
