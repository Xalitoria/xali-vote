package mc.xalitoria.vote.paper.proxy

import com.xalitoria.vote.protocol.Message
import com.xalitoria.vote.protocol.Protocol
import mc.xalitoria.vote.paper.XaliVote
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * This server's end of the proxy plugin (XaliVote on Velocity). The proxy shares its links and its party with the servers
 * ([Message.Settings], which is kept in `proxy-settings.dat` so that a restart does not lose it) and tells them when a party
 * started, and this server tells it the same when a party started here.
 *
 * Messages are signed with Velocity's forwarding secret, which Paper keeps in `config/paper-global.yml` (`proxies.velocity`), and
 * a message that is not signed with it is ignored: anything a client can send to a plugin channel reaches here too. A message
 * rides the connection of a player, so with nobody online none is sent and none arrives; the next player who joins brings the
 * proxy's settings.
 */
class ProxyLink(private val plugin: XaliVote, private val secret: String) : PluginMessageListener {
    private val file get() = plugin.dataPath.resolve("proxy-settings.dat")

    fun start() {
        plugin.server.messenger.registerIncomingPluginChannel(plugin, Protocol.CHANNEL, this)
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL)
    }

    fun stop() {
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, Protocol.CHANNEL, this)
        plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, Protocol.CHANNEL)
    }

    /** The settings the proxy shared last time, from the file; null if there are none. */
    fun saved(): Message.Settings? = try {
        if (file.exists()) Protocol.decode(file.readBytes()) as? Message.Settings else null
    } catch (e: Exception) {
        plugin.logger.warning("Could not read proxy-settings.dat: ${e.message}")
        null
    }

    /** Tells the proxy, which tells the other servers, that party number [number] started here. Nothing happens with nobody online to carry it. */
    fun publishParty(number: Long) {
        val carrier = plugin.server.onlinePlayers.firstOrNull() ?: return
        try {
            carrier.sendPluginMessage(plugin, Protocol.CHANNEL, Protocol.seal(secret, Message.Party(number)))
        } catch (e: Exception) {
            plugin.logger.warning("Could not tell the proxy about party #$number: ${e.message}")
        }
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != Protocol.CHANNEL) return
        val received = runCatching { Protocol.open(secret, message) }.getOrNull()
        if (received == null) {
            plugin.debug("Ignored a message from ${player.name} with a missing or wrong signature")
            return
        }
        when (received) {
            is Message.Settings -> {
                try {
                    file.writeBytes(Protocol.encode(received))
                } catch (e: Exception) {
                    plugin.logger.warning("Could not save proxy-settings.dat: ${e.message}")
                }
                plugin.applyShared(received)
            }
            is Message.Party -> plugin.flow.joinParty(received.number)
        }
    }
}
