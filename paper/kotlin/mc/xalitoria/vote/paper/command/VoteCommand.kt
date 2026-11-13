package mc.xalitoria.vote.paper.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
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
    val aliases = listOf("vote", "votes", "xvote")
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
            sender send msg.noMenu
            links(sender)
        }
    }

    private fun links(sender: CommandSender) {
        val links = plugin.links
        if (links.isEmpty()) return sender send msg.noLinks
        sender send msg.linksHeader
        for (link in links) {
            val text = msg.linkLine.replace("{name}", link.name) + if (link.description.isBlank()) "" else msg.linkDescription.replace("{description}", link.description)
            val hover = msg.linkHover.replace("{url}", link.url)
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
        if (places.isEmpty()) return sender send msg.topEmpty
        sender send msg.topHeader
        for (place in places) {
            sender send msg.topLine.replace("{position}", place.position.toString()).replace("{name}", place.name.verbatim()).replace("{votes}", place.votes.toString())
        }
    }

    private fun party(sender: CommandSender) {
        val party = plugin.party
        if (!party.enabled) return sender send msg.partyOff
        val status = plugin.board.party
        sender send msg.partyHeader
        sender send msg.partyProgress
            .replace("{progress}", status.progress.toString())
            .replace("{votes}", party.votes.toString())
            .replace("{remaining}", (party.votes - status.progress).coerceAtLeast(0).toString())
            .replace("{parties}", status.parties.toString())
    }

    private fun stats(sender: CommandSender, name: String?) {
        val target = name ?: (sender as? Player)?.name ?: return sender send msg.notPlayer
        plugin.scope.launch {
            try {
                val total = dbQuery { VoteCountRepository.total(target) }
                if (total == 0L && name != null) sender send msg.unknownPlayer.replace("{name}", name.verbatim())
                else sender send msg.votes.replace("{name}", target.verbatim()).replace("{votes}", total.toString())
            } catch (e: Exception) {
                plugin.logger.warning("Could not read the votes of $target: ${e.message}")
                sender send msg.databaseFailed
            }
        }
    }

    private fun reload(sender: CommandSender) {
        try {
            plugin.reload()
            sender send msg.reloaded
                .replace("{links}", plugin.links.size.toString())
                .replace("{party}", if (plugin.party.enabled) "every ${plugin.party.votes} votes" else "off")
                .replace("{proxy}", if (plugin.proxyActive) "linked" else "not linked")
        } catch (e: Exception) {
            sender send msg.reloadFailed.replace("{error}", e.message.orEmpty().verbatim())
        }
    }

    companion object {
        const val USE = "xalivote.use"
        const val ADMIN = "xalivote.admin"
    }
}
