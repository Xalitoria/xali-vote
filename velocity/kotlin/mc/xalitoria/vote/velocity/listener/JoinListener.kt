package mc.xalitoria.vote.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.VoteRepository
import com.xalitoria.vote.resolveCommands
import kotlinx.coroutines.launch
import mc.xalitoria.vote.velocity.XaliVote

class JoinListener(private val plugin: XaliVote) {

    @Subscribe
    fun onLogin(event: PostLoginEvent) {
        val playerName = event.player.username
        plugin.scope.launch {
            val voteNumbers = dbQuery { VoteRepository.claimAll(playerName) }
            if (voteNumbers.isEmpty()) return@launch
            val commands = voteNumbers.flatMap { resolveCommands(it, plugin.rewardsConfig) }
            plugin.logger.info("Dispensing rewards for ${voteNumbers.size} pending vote(s) to $playerName")
            commands.forEach { cmd -> plugin.dispatchCommand(playerName, cmd) }
        }
    }
}
