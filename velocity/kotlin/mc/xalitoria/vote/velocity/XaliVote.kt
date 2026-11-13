package mc.xalitoria.vote.velocity

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import com.xalitoria.util.config.YamlFile
import com.xalitoria.vote.VoteFlow
import com.xalitoria.vote.VoteHost
import com.xalitoria.vote.config.RewardEntry
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.db.DatabaseManager
import com.xalitoria.vote.defaultRewardsConfig
import com.xalitoria.vote.protocol.ForwardingSecret
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import mc.xalitoria.vote.velocity.listener.JoinListener
import mc.xalitoria.vote.velocity.listener.VoteListener
import mc.xalitoria.vote.velocity.relay.Relay
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.nio.file.Path

/**
 * The real plugin, created by the loader the Gradle plugin generates (XaliVoteVelocityLoader) once the libraries are on the classpath.
 * [plugin] is the loader instance Velocity knows about, used for event registration.
 *
 * On the proxy the votes are counted, rewarded and queued as on a server, but a console command here is the proxy's own. What reaches
 * the servers is the party (every server runs its `commands` for its players) and the settings the servers share, see [Relay].
 */
class XaliVote(
    val server: ProxyServer,
    val logger: Logger,
    val dataDirectory: Path,
    private val plugin: Any,
) : Runnable, AutoCloseable, VoteHost {
    private val configFile by lazy {
        YamlFile(dataDirectory.resolve("config.yml"), VoteConfig.serializer(), ::VoteConfig)
    }
    private val rewardsConfigFile by lazy {
        YamlFile(
            dataDirectory.resolve("rewards.yml"),
            MapSerializer(String.serializer(), RewardEntry.serializer()),
            ::defaultRewardsConfig,
        )
    }

    val voteConfig: VoteConfig get() = configFile.data
    val rewardsConfig: Map<String, RewardEntry> get() = rewardsConfigFile.data

    /** What the events and commands are registered with. */
    val loader: Any get() = plugin

    lateinit var scope: CoroutineScope
        private set

    lateinit var flow: VoteFlow
        private set

    /** The link with the servers; null while `proxy.enabled` is off or the forwarding secret cannot be read. */
    private var relay: Relay? = null

    // The platform, for VoteFlow.
    override val rewards: Map<String, RewardEntry> get() = rewardsConfig
    override val party: VoteConfig.Party get() = voteConfig.party
    override fun isOnline(playerName: String): Boolean = server.getPlayer(playerName).isPresent

    /** With the relay the servers run the party's commands for their players, so the proxy does not run them a second time. */
    override fun partyPlayers(): List<String> = if (relay != null) [] else server.allPlayers.map { it.username }

    override fun console(command: String) {
        server.commandManager.executeAsync(server.consoleCommandSource, command)
    }

    /** The proxy has no vouchers to give; XaliRedeem is a Paper plugin. */
    override fun voucherCommand(id: String, amount: Int): String? = null

    override fun partyStarted(number: Long) {
        relay?.partyStarted(number)
    }

    override fun info(message: String) = logger.info(message)

    /** Enables the plugin. */
    override fun run() {
        scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t ->
                logger.error("Unhandled coroutine exception", t)
            }
        )

        configFile.load()
        rewardsConfigFile.load()
        configFile.save()

        try {
            DatabaseManager.connect(dataDirectory.toFile(), voteConfig.database)
        } catch (e: Exception) {
            logger.error("Failed to connect to database, disabling: ${e.message}")
            return
        }

        flow = VoteFlow(this)
        warnAboutVouchers()
        server.eventManager.register(plugin, VoteListener(this))
        server.eventManager.register(plugin, JoinListener(this))
        enableRelay()
        registerCommand()

        logger.info("XaliVote enabled.")
    }

    /** Disables the plugin. */
    override fun close() {
        relay?.stop()
        if (::scope.isInitialized) scope.cancel("XaliVoteVelocity shutting down")
        DatabaseManager.close()
    }

    /**
     * Reads the files again; one that does not parse changes nothing and the error is thrown. The servers that have a player on are
     * told the new links and party at once, and the others when someone connects.
     */
    fun reload() {
        YamlFile(dataDirectory.resolve("config.yml"), VoteConfig.serializer(), ::VoteConfig).load()
        YamlFile(
            dataDirectory.resolve("rewards.yml"),
            MapSerializer(String.serializer(), RewardEntry.serializer()),
            ::defaultRewardsConfig,
        ).load()
        configFile.load()
        rewardsConfigFile.load()
        configFile.save()
        warnAboutVouchers()
        enableRelay()
        relay?.shareAll()
    }

    private fun warnAboutVouchers() {
        val named = rewardsConfig.filterValues { it.vouchers.isNotEmpty() }.keys
        if (named.isNotEmpty()) logger.warn("rewards.yml gives vouchers in {}, which only the Paper plugin can do, so the proxy leaves them out", named.joinToString())
    }

    /** Starts or stops the link with the servers by what `proxy.enabled` says and whether the forwarding secret can be read. */
    private fun enableRelay() {
        if (!voteConfig.proxy.enabled) {
            if (relay != null) logger.info("proxy.enabled is off: the servers keep their own links and party, and the proxy runs the party's commands itself")
            relay?.stop()
            relay = null
            return
        }
        if (relay != null) return
        val secret = ForwardingSecret.ofProxy(dataDirectory.toAbsolutePath().parent.parent)
        if (secret == null) {
            logger.error("Could not read the forwarding secret, so the servers are told nothing and the proxy runs the party's commands itself")
            return
        }
        relay = Relay(this, secret).also { it.start() }
        logger.info("Sharing the links and the party with the servers on {}", com.xalitoria.vote.protocol.Protocol.CHANNEL)
    }

    private fun registerCommand() {
        val command = BrigadierCommand(
            LiteralArgumentBuilder.literal<CommandSource>("xalivotev")
                .requires { it.hasPermission("xalivote.admin") }
                .executes {
                    it.source.sendMessage(Component.text("XaliVote on the proxy: ${voteConfig.links.size} link(s), party ${if (party.enabled) "every ${party.votes} votes" else "off"}, servers ${if (relay != null) "told" else "not told"}."))
                    Command.SINGLE_SUCCESS
                }
                .then(
                    LiteralArgumentBuilder.literal<CommandSource>("reload").executes {
                        try {
                            reload()
                            it.source.sendMessage(Component.text("Reloaded; the servers that have a player on were told."))
                        } catch (e: Exception) {
                            it.source.sendMessage(Component.text("The configuration is wrong, so nothing changed: ${e.message}"))
                        }
                        Command.SINGLE_SUCCESS
                    },
                ),
        )
        server.commandManager.register(server.commandManager.metaBuilder(command).plugin(plugin).build(), command)
    }
}
