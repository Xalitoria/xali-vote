package mc.xalitoria.vote.paper

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import com.xalitoria.util.bukkit.coroutineScope
import com.xalitoria.util.bukkit.register
import com.xalitoria.util.bukkit.runConsoleCommand
import com.xalitoria.util.config.YamlFile
import com.xalitoria.vote.VoteFlow
import com.xalitoria.vote.VoteHost
import com.xalitoria.vote.config.RewardEntry
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.db.DatabaseManager
import com.xalitoria.vote.defaultRewardsConfig
import com.xalitoria.vote.protocol.ForwardingSecret
import com.xalitoria.vote.protocol.Message
import com.xalitoria.vote.usableLinks
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import mc.xalitoria.vote.paper.board.Board
import mc.xalitoria.vote.paper.command.VoteCommand
import mc.xalitoria.vote.paper.data.MessageConfig
import mc.xalitoria.vote.paper.external.MenuHook
import mc.xalitoria.vote.paper.external.RedeemHook
import mc.xalitoria.vote.paper.external.VoteExpansion
import mc.xalitoria.vote.paper.http.VoteWebhook
import mc.xalitoria.vote.paper.link.ServerLinkSync
import mc.xalitoria.vote.paper.listener.JoinListener
import mc.xalitoria.vote.paper.listener.VoteListener
import mc.xalitoria.vote.paper.proxy.ProxyLink
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path

class XaliVote : JavaPlugin(), VoteHost {
    private val configFile by lazy {
        YamlFile(dataFolder.toPath().resolve("config.yml"), VoteConfig.serializer(), ::VoteConfig)
    }
    private val rewardsConfigFile by lazy {
        YamlFile(
            dataFolder.toPath().resolve("rewards.yml"),
            MapSerializer(String.serializer(), RewardEntry.serializer()),
            ::defaultRewardsConfig,
        )
    }
    private val messagesFile by lazy {
        messagesFile(dataFolder.toPath().resolve("messages.yml"))
    }

    val voteConfig: VoteConfig get() = configFile.data
    val rewardsConfig: Map<String, RewardEntry> get() = rewardsConfigFile.data
    val messages: MessageConfig get() = messagesFile.data

    lateinit var scope: CoroutineScope
        private set

    lateinit var flow: VoteFlow
        private set

    lateinit var board: Board
        private set

    private val redeem = RedeemHook(this)
    private val serverLinkSync = ServerLinkSync(this)
    private var webhook: VoteWebhook? = null
    private var expansion: VoteExpansion? = null
    private var debugging = false

    /** XaliMenu's side, when `menu.enabled` is on and XaliMenu is installed. */
    var menu: MenuHook? = null
        private set

    /** The proxy plugin's end, when this server is behind Velocity and `proxy.enabled` is on. */
    var proxyLink: ProxyLink? = null
        private set

    /** What the proxy plugin shared last: the links and the party, which then take the place of this server's own. */
    @Volatile
    private var shared: Message.Settings? = null

    /** The links in use: the proxy's if it shares them, else `links` of the configuration; only those a player can be sent to. */
    @Volatile
    var links: List<VoteConfig.Link> = []
        private set

    override val party: VoteConfig.Party get() = shared?.party ?: voteConfig.party

    /** Whether this server is behind a proxy that has the plugin: it has the forwarding secret and `proxy.enabled` is on. */
    val proxyActive: Boolean get() = proxyLink != null

    // The platform, for VoteFlow.
    override val rewards: Map<String, RewardEntry> get() = rewardsConfig
    override fun isOnline(playerName: String): Boolean = server.getPlayerExact(playerName) != null
    override fun partyPlayers(): List<String> = server.onlinePlayers.map { it.name }
    override fun console(command: String) { runConsoleCommand(command) }
    override fun voucherCommand(id: String, amount: Int): String? = redeem.command(id, amount)
    override fun counted(playerName: String, total: Long) = board.counted(playerName, total)
    override fun partyStarted(number: Long) { proxyLink?.publishParty(number) }
    override fun info(message: String) = logger.info(message)

    fun debug(message: String) {
        if (debugging) logger.info(message)
    }

    override fun onEnable() {
        scope = coroutineScope()

        try {
            loadFiles()
        } catch (e: Exception) {
            logger.severe("The configuration is wrong, disabling: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        try {
            DatabaseManager.connect(dataFolder, voteConfig.database)
        } catch (e: Exception) {
            logger.severe("Failed to connect to database, disabling: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        server.pluginManager.addPermission(Permission(VoteCommand.USE, PermissionDefault.TRUE))
        server.pluginManager.addPermission(Permission(VoteCommand.ADMIN, PermissionDefault.OP))

        flow = VoteFlow(this)
        board = Board(this)
        board.start()

        val voteListener = VoteListener(this)
        server.pluginManager.registerEvents(voteListener, this)
        server.pluginManager.registerEvents(JoinListener(this), this)

        if (voteConfig.webhook.enabled) {
            try {
                webhook = VoteWebhook(this, voteListener).also { it.start(voteConfig.webhook.port) }
                logger.info("Webhook listening on port ${voteConfig.webhook.port}")
                if (voteConfig.webhook.secret.isEmpty()) {
                    logger.warning("webhook.secret is empty, so anyone who can reach the port can queue votes")
                }
            } catch (e: Exception) {
                // NuVotifier's own default port is the same as this one's, which is the usual reason.
                logger.severe("The webhook could not listen on port ${voteConfig.webhook.port} (${e.message}), so it is off; set webhook.port to a free one")
            }
        }

        enableProxy()
        enableHooks()
        applySettings()

        val command = VoteCommand(this)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(this, command.builder().build(), command.description, command.aliases)
        }

        logger.info("XaliVote enabled.")
    }

    override fun onDisable() {
        webhook?.stop()
        proxyLink?.stop()
        expansion?.unregister()
        if (::scope.isInitialized) scope.cancel("XaliVote disabling")
        serverLinkSync.clear()
        DatabaseManager.close()
    }

    /**
     * Reads the files again. A file that does not parse changes nothing and the error is thrown. The proxy link and the hooks are
     * looked at again too, but a server's forwarding secret is only read at startup, as Paper does.
     */
    fun reload() {
        // Each is read on its own first, so that one that does not parse stops the reload before anything has changed.
        YamlFile(dataFolder.toPath().resolve("config.yml"), VoteConfig.serializer(), ::VoteConfig).load()
        YamlFile(
            dataFolder.toPath().resolve("rewards.yml"),
            MapSerializer(String.serializer(), RewardEntry.serializer()),
            ::defaultRewardsConfig,
        ).load()
        messagesFile(dataFolder.toPath().resolve("messages.yml")).load()
        loadFiles()
        enableProxy()
        enableHooks()
        applySettings()
    }

    /** The messages are written in kebab case, like the rest of the keys: `links-header`. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun messagesFile(path: Path) = YamlFile(
        path,
        MessageConfig.serializer(),
        ::MessageConfig,
        Yaml(configuration = YamlConfiguration(strictMode = false, encodeDefaults = true, yamlNamingStrategy = YamlNamingStrategy.KebabCase)),
    )

    private fun loadFiles() {
        configFile.load()
        rewardsConfigFile.load()
        messagesFile.load()
        // Written again so that a setting added by an update shows up in the file.
        configFile.save()
        messagesFile.save()
    }

    /** The proxy plugin shared [settings]: they replace this server's links and party until the proxy says otherwise. */
    fun applyShared(settings: Message.Settings) {
        shared = settings
        applySettings()
    }

    /** Works out the links and the Server Links screen from what is in force. */
    private fun applySettings() {
        val source = shared?.links ?: voteConfig.links
        links = usableLinks(source) { logger.warning("Link '${it.name}' has no name or no http(s) address (${it.url}), so it is left out") }
        serverLinkSync.apply(links, shared?.serverLinks ?: voteConfig.serverLinks)
    }

    /** Starts or stops the link to the proxy plugin by what `proxy.enabled` says and whether Velocity forwarding is set up. */
    private fun enableProxy() {
        val wanted = voteConfig.proxy.enabled
        if (!wanted) {
            if (proxyLink != null) logger.info("proxy.enabled is off: this server uses its own links and party again")
            proxyLink?.stop()
            proxyLink = null
            shared = null
            return
        }
        if (proxyLink != null) return
        val secret = ForwardingSecret.ofServer(server.pluginsFolder.toPath().toAbsolutePath().parent)
        if (secret.isBlank()) {
            debug("No Velocity forwarding in config/paper-global.yml: not behind a proxy, or it is not set up yet")
            return
        }
        val link = ProxyLink(this, secret).also { it.start() }
        proxyLink = link
        shared = link.saved()
        logger.info(
            "Velocity forwarding found: the links and the party come from the proxy plugin" +
                (if (shared == null) " once a player brings them (until then this server's own are used)" else " (the last it shared are in use)"),
        )
    }

    private fun enableHooks() {
        redeem.warnIfMissing()
        val pm = server.pluginManager
        if (expansion == null && pm.isPluginEnabled("PlaceholderAPI")) {
            expansion = VoteExpansion(this).also { it.register() }
        }
        val settings = voteConfig.menu
        if (!settings.enabled) {
            if (menu != null) logger.info("menu.enabled is off: /vote lists the links in chat. XaliMenu keeps the menus until the server restarts.")
            menu = null
            return
        }
        if (menu != null) return
        if (!pm.isPluginEnabled("XaliMenu")) {
            logger.info("menu.enabled is on, but XaliMenu is not installed: /vote lists the links in chat instead")
            return
        }
        try {
            val hook = MenuHook(this)
            menu = hook
            if (settings.writeMenus && hook.writeMenus()) logger.info("The vote menus are in plugins/XaliMenu/menus; /xmenu reload reads them if XaliMenu has already started")
        } catch (e: Exception) {
            logger.severe("Failed to connect to XaliMenu; /vote lists the links in chat instead: ${e.message}")
        }
    }
}
