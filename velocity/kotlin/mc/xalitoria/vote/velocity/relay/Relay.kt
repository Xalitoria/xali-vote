package mc.xalitoria.vote.velocity.relay

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.util.ServerLink
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.protocol.Message
import com.xalitoria.vote.protocol.Protocol
import com.xalitoria.vote.usableLinks
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import mc.xalitoria.vote.velocity.XaliVote
import java.util.concurrent.ConcurrentHashMap

/**
 * The proxy's end of the link with the servers (XaliVote on Paper): it tells them the settings they share, the links and the party,
 * when a player connects to one and when the configuration is reloaded, and passes on the news that a party started, whether it
 * started here or on a server.
 *
 * Everything is signed with the forwarding secret from `velocity.toml`; a server ignores a message that is not. What a player sends on
 * the channel is dropped here, and only what comes from a connection to a server is passed on, which a player cannot make. A message
 * rides the connection of a player, so a server with nobody on cannot be told: the first player who connects brings it.
 */
class Relay(private val plugin: XaliVote, private val secret: String) {
    private val channel = MinecraftChannelIdentifier.from(Protocol.CHANNEL)

    /** When each server was last sent the settings, so that a player who switches back and forth does not send them over and over. */
    private val sent = ConcurrentHashMap<String, Long>()

    fun start() {
        plugin.server.channelRegistrar.register(channel)
        plugin.server.eventManager.register(plugin.loader, this)
    }

    fun stop() {
        plugin.server.eventManager.unregisterListener(plugin.loader, this)
        plugin.server.channelRegistrar.unregister(channel)
    }

    /** The settings as they are now, as one message; null if they do not fit (which is logged). */
    private fun settings(): ByteArray? {
        val config = plugin.voteConfig
        val links = usableLinks(config.links) { plugin.logger.warn("Link '{}' has no name or no http(s) address ({}), so it is left out", it.name, it.url) }
        return try {
            Protocol.seal(secret, Message.Settings(links, config.serverLinks, config.party))
        } catch (e: IllegalArgumentException) {
            plugin.logger.error("The settings are too big to share with the servers: {}", e.message)
            null
        }
    }

    /** Sends the settings to every server that has a player on it. */
    fun shareAll() {
        val frame = settings() ?: return
        for (server in plugin.server.allServers) {
            val carrier = server.playersConnected.firstOrNull() ?: continue
            carrier.currentServer.ifPresent { it.sendPluginMessage(channel, frame) }
            sent[server.serverInfo.name] = System.currentTimeMillis()
        }
    }

    /** Tells every server that party number [number] started. */
    fun partyStarted(number: Long) {
        val frame = try {
            Protocol.seal(secret, Message.Party(number))
        } catch (e: IllegalArgumentException) {
            return
        }
        for (server in plugin.server.allServers) {
            val carrier = server.playersConnected.firstOrNull() ?: continue
            carrier.currentServer.ifPresent { it.sendPluginMessage(channel, frame) }
        }
    }

    @Subscribe
    fun onConnected(event: ServerPostConnectEvent) {
        val player = event.player
        val connection = player.currentServer.orElse(null) ?: return
        val name = connection.serverInfo.name
        val now = System.currentTimeMillis()
        if (now - (sent[name] ?: 0L) >= DEBOUNCE_MILLIS) {
            settings()?.let { connection.sendPluginMessage(channel, it) }
            sent[name] = now
        }
        serverLinks(player)
    }

    /** Puts the vote links in the player's Server Links screen when `server-links.enabled` is on. */
    private fun serverLinks(player: Player) {
        val config = plugin.voteConfig
        if (!config.serverLinks.enabled) return
        val serializer = LegacyComponentSerializer.legacyAmpersand()
        val links = usableLinks(config.links).map { ServerLink.serverLink(serializer.deserialize(config.serverLinks.prefix + it.name), it.url) }
        if (links.isNotEmpty()) player.setServerLinks(links)
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != channel) return
        // Whichever way it came, it goes no further than this: a client must never see it, and a server's own plugin handles it.
        event.result = PluginMessageEvent.ForwardResult.handled()
        // A message from a player is a client trying to forge one.
        val source = event.source as? ServerConnection ?: return
        val data = event.data
        if (!Protocol.isFrame(data)) return
        val origin = source.serverInfo.name
        for (target in plugin.server.allServers) {
            if (target.serverInfo.name == origin || target.playersConnected.isEmpty()) continue
            target.sendPluginMessage(channel, data)
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 5_000L
    }
}
