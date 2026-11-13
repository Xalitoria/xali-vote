package mc.xalitoria.vote.velocity

import com.velocitypowered.api.proxy.ProxyServer
import com.xalitoria.util.config.YamlFile
import com.xalitoria.vote.config.RewardEntry
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.db.DatabaseManager
import com.xalitoria.vote.defaultRewardsConfig
import com.xalitoria.vote.withPlayer
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import mc.xalitoria.vote.velocity.listener.JoinListener
import mc.xalitoria.vote.velocity.listener.VoteListener
import org.slf4j.Logger
import java.nio.file.Path

/**
 * The real plugin, created by the loader the Gradle plugin generates (XaliVoteVelocityLoader) once the libraries are on the classpath.
 * [plugin] is the loader instance Velocity knows about, used for event registration.
 */
class XaliVote(
    val server: ProxyServer,
    val logger: Logger,
    val dataDirectory: Path,
    private val plugin: Any,
) : Runnable, AutoCloseable {
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

    lateinit var scope: CoroutineScope
        private set

    /** Whether a player with this exact name is online. */
    fun isOnline(playerName: String): Boolean = server.getPlayer(playerName).isPresent

    /** Runs [command] as the console, with `{player}` replaced by [playerName]. */
    fun dispatchCommand(playerName: String, command: String) {
        server.commandManager.executeAsync(server.consoleCommandSource, command.withPlayer(playerName))
    }

    /** Enables the plugin. */
    override fun run() {
        scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t ->
                logger.error("Unhandled coroutine exception", t)
            }
        )

        configFile.load()
        rewardsConfigFile.load()

        try {
            DatabaseManager.connect(dataDirectory.toFile(), voteConfig.database)
        } catch (e: Exception) {
            logger.error("Failed to connect to database, disabling: ${e.message}")
            return
        }

        server.eventManager.register(plugin, VoteListener(this))
        server.eventManager.register(plugin, JoinListener(this))

        logger.info("XaliVote enabled.")
    }

    /** Disables the plugin. */
    override fun close() {
        scope.cancel("XaliVoteVelocity shutting down")
        DatabaseManager.close()
    }

    fun reload() {
        configFile.load()
        rewardsConfigFile.load()
    }
}
