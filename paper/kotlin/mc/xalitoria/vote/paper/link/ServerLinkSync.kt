package mc.xalitoria.vote.paper.link

import com.xalitoria.util.message.toComponent
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.webAddress
import mc.xalitoria.vote.paper.XaliVote
import org.bukkit.ServerLinks

/**
 * Keeps the vote links in the game's own Server Links screen (the pause menu) when `server-links.enabled` is on. It removes
 * the ones it added before putting the current ones, so a reload or new settings from the proxy never leave old links, and
 * touches no link that another plugin or the server's configuration added. Players see a change the next time they join.
 */
class ServerLinkSync(private val plugin: XaliVote) {
    private val added = ArrayList<ServerLinks.ServerLink>()

    fun apply(links: List<VoteConfig.Link>, setting: VoteConfig.ServerLinks) {
        val registry = plugin.server.serverLinks
        added.forEach(registry::removeLink)
        added.clear()
        if (!setting.enabled) return
        for (link in links) {
            val address = webAddress(link.url) ?: continue
            added += registry.addLink((setting.prefix + link.name).toComponent(), address)
        }
    }

    fun clear() = apply([], VoteConfig.ServerLinks(enabled = false))
}
