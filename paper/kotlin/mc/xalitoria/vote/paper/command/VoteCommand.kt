package mc.xalitoria.vote.paper.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.xalitoria.util.message.LocalizedPlaceholders
import com.xalitoria.util.message.fail
import com.xalitoria.util.message.info
import com.xalitoria.util.message.pass
import com.xalitoria.util.message.send
import com.xalitoria.util.message.toComponent
import com.xalitoria.util.message.verbatim
import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.VoteCountRepository
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import mc.xalitoria.vote.paper.XaliVote
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/xalivote`, also `/vote`: where to vote, the top voters, the party, and a player's votes. Whatever reads the database runs off the
 * region thread and answers when it has the result.
 */
class VoteCommand(private val plugin: XaliVote) {
    val label = "xalivote"
    val aliases = listOf("vote", "xvote", "votes", "xvotes")
    val description = "Vote for the server"

    private val msg get() = plugin.messages

    fun builder(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(label)
        .requires { it.sender.hasPermission(USE) }
        .executes { run(it) { sender -> home(sender) } }
        .then(Commands.literal("links").executes { run(it) { sender -> links(sender) } })
        .then(Commands.literal("menu").executes { run(it) { sender -> menu(sender) } })
        .then(Commands.literal("top").executes { run(it) { sender -> top(sender) } })
        .then(Commands.literal("party").executes { run(it) { sender -> party(sender) } })
        .then(Commands.literal("stats")
            .executes { run(it) { sender -> stats(sender, null) } }
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests { context, builder ->
                    plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(builder.remaining, ignoreCase = true) }.forEach(builder::suggest)
                    builder.buildFuture()
                }
                .executes { run(it) { sender -> stats(sender, it.getArgument("player", String::class.java)) } },
            ),
        )
        .then(Commands.literal("reload")
            .requires { it.sender.hasPermission(ADMIN) }
            .executes { run(it) { sender -> reload(sender) } },
        )

    private fun run(context: CommandContext<CommandSourceStack>, block: (CommandSender) -> Unit): Int {
        block(context.source.sender)
        return 1
    }

    /** The menu if there is one for a player, else the list. */
    private fun home(sender: CommandSender) {
        val player = sender as? Player
        if (player != null && plugin.menu?.openVote(player) == true) return
        links(sender)
    }

    private fun menu(sender: CommandSender) {
        val player = sender as? Player ?: return links(sender)
        if (plugin.menu?.openVote(player) != true) {
            sender info msg.noMenu
            links(sender)
        }
    }

    private fun links(sender: CommandSender) {
        val links = plugin.links
        if (links.isEmpty()) return sender fail msg.noLinks
        sender send msg.linksHeader
        for (link in links) {
            val text = fill(msg.linkLine, "name" to link.name) + if (link.description.isBlank()) "" else fill(msg.linkDescription, "description" to link.description)
            val hover = fill(msg.linkHover, "url" to link.url)
            sender.sendMessage(
                text.toComponent()
                    .clickEvent(ClickEvent.openUrl(link.url))
                    .hoverEvent(HoverEvent.showText(hover.toComponent())),
            )
        }
    }

    private fun top(sender: CommandSender) {
        val player = sender as? Player
        if (player != null && plugin.menu?.openTop(player) == true) return
        val places = plugin.board.top
        if (places.isEmpty()) return sender info msg.topEmpty
        sender send msg.topHeader
        for (place in places) {
            sender send fill(msg.topLine, "position" to place.position, "name" to place.name.verbatim(), "votes" to place.votes)
        }
    }

    private fun party(sender: CommandSender) {
        val party = plugin.party
        if (!party.enabled) return sender info msg.partyOff
        val status = plugin.board.party
        sender send msg.partyHeader
        sender send fill(
            msg.partyProgress,
            "progress" to status.progress,
            "votes" to party.votes,
            "remaining" to (party.votes - status.progress).coerceAtLeast(0),
            "parties" to status.parties,
        )
    }

    private fun stats(sender: CommandSender, name: String?) {
        val target = name ?: (sender as? Player)?.name ?: return sender fail msg.notPlayer
        plugin.scope.launch {
            try {
                val total = dbQuery { VoteCountRepository.total(target) }
                if (total == 0L && name != null) sender fail fill(msg.unknownPlayer, "name" to name.verbatim())
                else sender send fill(msg.votes, "name" to target.verbatim(), "votes" to total)
            } catch (e: Exception) {
                plugin.logger.warning("Could not read the votes of $target: ${e.message}")
                sender fail msg.databaseFailed
            }
        }
    }

    private fun reload(sender: CommandSender) {
        try {
            plugin.reload()
            sender pass fill(
                msg.reloaded,
                "links" to plugin.links.size,
                "party" to if (plugin.party.enabled) "every ${plugin.party.votes} votes" else "off",
                "proxy" to if (plugin.proxyActive) "linked" else "not linked",
            )
        } catch (e: Exception) {
            sender fail fill(msg.reloadFailed, "error" to e.message.orEmpty().verbatim())
        }
    }

    /** [template] with the values filled in: numbers are written grouped, and a template can pick a plural (`{votes|plural:vote,votes}`). */
    private fun fill(template: String, vararg values: Pair<String, Any?>) = LocalizedPlaceholders().with(*values).fill(template)

    companion object {
        const val USE = "xalivote.use"
        const val ADMIN = "xalivote.admin"
    }
}
