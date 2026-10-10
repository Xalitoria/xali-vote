package mc.xalitoria.vote.listener

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.xalitoria.vote.db.DatabaseManager.dbQuery
import net.xalitoria.vote.db.repository.VoteRepository
import net.xalitoria.vote.dispatchCommand
import mc.xalitoria.vote.XaliVote
import net.xalitoria.vote.resolveCommands
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class JoinListener(private val plugin: XaliVote) : Listener {

    @EventHandler(priority = MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name
        plugin.scope.launch {
            val voteNumbers = dbQuery { VoteRepository.claimAll(playerName) }
            if (voteNumbers.isEmpty()) return@launch
            val commands = voteNumbers.flatMap { resolveCommands(it, plugin.rewardsConfig) }
            plugin.logger.info("Dispensing rewards for ${voteNumbers.size} pending vote(s) to $playerName")
            withContext(Dispatchers.Main.immediate) {
                commands.forEach { cmd -> dispatchCommand(playerName, cmd) }
            }
        }
    }
}
