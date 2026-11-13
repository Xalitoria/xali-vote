package mc.xalitoria.vote.paper.listener

import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.VoteRepository
import com.xalitoria.vote.resolveCommands
import kotlinx.coroutines.launch
import mc.xalitoria.vote.paper.XaliVote
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
            commands.forEach { plugin.dispatchCommand(playerName, it) }
        }
    }
}
