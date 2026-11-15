package mc.xalitoria.vote.paper.external

import com.xalitoria.util.message.LocalizedPlaceholders
import com.xalitoria.util.bukkit.runTask
import com.xalitoria.vote.webAddress
import mc.xalitoria.vote.paper.XaliVote
import net.kyori.adventure.text.event.ClickEvent
import net.xalitoria.menu.Action
import net.xalitoria.menu.Catalog
import net.xalitoria.menu.MenuContext
import net.xalitoria.menu.XaliMenuApi
import org.bukkit.entity.Player
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * XaliMenu's side of this plugin. It is only made when `menu.enabled` is on and XaliMenu is installed, because it loads XaliMenu's
 * classes; everything else works without it.
 *
 * It adds, for any menu to use:
 *
 * - the catalog `xalivote:links`, the places to vote: `{index}`, `{name}`, `{url}`, `{host}` and `{description}`;
 * - the catalog `xalivote:top`, the top voters: `{position}`, `{name}`, `{votes}` and `{uuid}`; both are answered from memory;
 * - the action `xalivote:link`, `url = "{url}"`, which tells the player the link in chat, where it can be clicked, since a menu
 *   cannot open a web page by itself.
 *
 * And it writes the two menus that `/vote` and `/vote top` open.
 */
class MenuHook(private val plugin: XaliVote) {
    private val api = XaliMenuApi.get()

    init {
        api.registerCatalog(plugin, LINKS, LINK_VALUES) { LinkCatalog(plugin) }
        api.registerCatalog(plugin, TOP, TOP_VALUES) { TopCatalog(plugin) }
        api.registerAction(plugin, LINK) { s -> OpenLink(plugin, s.requireString("url"), s.string("name", "{name}")) }
    }

    private fun has(name: String): Boolean = name in api.menus

    private fun open(player: Player, name: String): Boolean {
        if (!has(name)) return false
        // Opening is done on the thread of the player.
        player.runTask(plugin) { api.open(player, name) }
        return true
    }

    /** Opens the vote menu for [player]; false if there is none, so the caller answers in chat. */
    fun openVote(player: Player): Boolean = open(player, plugin.voteConfig.menu.name)

    /** Opens the menu of the top voters for [player]; false if there is none. */
    fun openTop(player: Player): Boolean = open(player, plugin.voteConfig.menu.top)

    /** Writes the menus into XaliMenu's folder where they are missing; true if it wrote any, false if XaliMenu keeps no folder to write into. */
    fun writeMenus(): Boolean {
        val directory = plugin.server.pluginManager.getPlugin("XaliMenu")?.dataPath?.resolve("menus") ?: return false
        val settings = plugin.voteConfig.menu
        var wrote = false
        for ([name, resource] in listOf(settings.name to "/menu/vote.conf", settings.top to "/menu/votetop.conf")) {
            val file = directory.resolve("$name.conf")
            if (file.exists()) continue
            val text = MenuHook::class.java.getResourceAsStream(resource)?.use { String(it.readAllBytes(), Charsets.UTF_8) } ?: continue
            directory.createDirectories()
            file.writeText(text)
            wrote = true
        }
        return wrote
    }

    private class LinkCatalog(private val plugin: XaliVote) : Catalog {
        override fun elements(ctx: MenuContext): List<Map<String, String>> = plugin.links.mapIndexed { index, link ->
            mapOf(
                "index" to (index + 1).toString(),
                "name" to link.name,
                "url" to link.url,
                "host" to (webAddress(link.url)?.host ?: ""),
                "description" to link.description,
            )
        }
    }

    private class TopCatalog(private val plugin: XaliVote) : Catalog {
        override fun elements(ctx: MenuContext): List<Map<String, String>> = plugin.board.top.map { place ->
            mapOf(
                "position" to place.position.toString(),
                "name" to place.name,
                "votes" to place.votes.toString(),
                "uuid" to (plugin.server.getOfflinePlayerIfCached(place.name)?.uniqueId?.toString() ?: ""),
            )
        }
    }

    /** `{ type = "xalivote:link", url = "{url}" }`: the link in chat, clickable. Only a web address is sent, whatever the menu says. */
    private class OpenLink(private val plugin: XaliVote, private val url: String, private val name: String) : Action {
        override fun run(ctx: MenuContext) {
            val address = webAddress(ctx.parse(url)) ?: return
            val label = ctx.parse(name).ifBlank { address.host }
            val line = LocalizedPlaceholders().with("url", address.toString()).fill(plugin.messages.linkOpened).let { ctx.component(it) }
            ctx.player.sendMessage(line.clickEvent(ClickEvent.openUrl(address.toString())))
            plugin.debug("${ctx.player.name} was sent the link $label")
        }
    }

    companion object {
        const val LINKS = "xalivote:links"
        const val TOP = "xalivote:top"
        const val LINK = "xalivote:link"

        /** What an element holds, with an example of each, for the editor. */
        private val LINK_VALUES = mapOf(
            "index" to "1", "name" to "Minecraft Servers", "url" to "https://example.com/vote", "host" to "example.com",
            "description" to "Once a day",
        )
        private val TOP_VALUES = mapOf(
            "position" to "1", "name" to "Notch", "votes" to "123", "uuid" to "069a79f4-44e9-4726-a5be-fca90e38aaf5",
        )
    }
}
