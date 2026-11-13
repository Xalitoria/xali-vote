package mc.xalitoria.vote.paper

import com.xalitoria.util.bukkit.coroutineScope
import com.xalitoria.util.bukkit.runConsoleCommand
import com.xalitoria.util.config.YamlFile
import com.xalitoria.vote.config.RewardEntry
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.db.DatabaseManager
import com.xalitoria.vote.defaultRewardsConfig
import com.xalitoria.vote.withPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import mc.xalitoria.vote.paper.http.VoteWebhook
import mc.xalitoria.vote.paper.listener.JoinListener
import mc.xalitoria.vote.paper.listener.VoteListener
import org.bukkit.plugin.java.JavaPlugin

class XaliVote : JavaPlugin() {
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

    val voteConfig: VoteConfig get() = configFile.data
    val rewardsConfig: Map<String, RewardEntry> get() = rewardsConfigFile.data

    lateinit var scope: CoroutineScope
        private set

    private var webhook: VoteWebhook? = null

    /** Whether a player with this exact name is online. */
    fun isOnline(playerName: String): Boolean = server.getPlayerExact(playerName) != null

    /** Runs [command] as the console, with `{player}` replaced by [playerName]. Safe to call from any thread. */
    fun dispatchCommand(playerName: String, command: String) {
        runConsoleCommand(command.withPlayer(playerName))
    }

    override fun onEnable() {
        scope = coroutineScope()

        configFile.load()
        rewardsConfigFile.load()

        try {
            DatabaseManager.connect(dataFolder, voteConfig.database)
        } catch (e: Exception) {
            logger.severe("Failed to connect to database, disabling: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        val voteListener = VoteListener(this)
        server.pluginManager.registerEvents(voteListener, this)
        server.pluginManager.registerEvents(JoinListener(this), this)

        if (voteConfig.webhook.enabled) {
            webhook = VoteWebhook(this, voteListener).also { it.start(voteConfig.webhook.port) }
            logger.info("Webhook listening on port ${voteConfig.webhook.port}")
            if (voteConfig.webhook.secret.isEmpty()) {
                logger.warning("webhook.secret is empty, so anyone who can reach the port can queue votes")
            }
        }

        logger.info("XaliVote enabled.")
    }

    override fun onDisable() {
        webhook?.stop()
        scope.cancel("XaliVote disabling")
        DatabaseManager.close()
    }

    fun reload() {
        configFile.load()
        rewardsConfigFile.load()
    }
}
