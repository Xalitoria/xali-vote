package mcv.xalitoria.vote.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import kotlinx.coroutines.launch
import net.xalitoria.vote.db.DatabaseManager.dbQuery
import net.xalitoria.vote.db.repository.VoteRepository
import net.xalitoria.vote.dispatchCommand
import net.xalitoria.vote.resolveCommands
import mcv.xalitoria.vote.XaliVoteVelocity

class JoinListener(private val plugin: XaliVoteVelocity) {

    @Subscribe
    fun onLogin(event: PostLoginEvent) {
        val playerName = event.player.username
        plugin.scope.launch {
            val voteNumbers = dbQuery { VoteRepository.claimAll(playerName) }
            if (voteNumbers.isEmpty()) return@launch
            val commands = voteNumbers.flatMap { resolveCommands(it, plugin.rewardsConfig) }
            plugin.logger.info("Dispensing rewards for ${voteNumbers.size} pending vote(s) to $playerName")
            commands.forEach { cmd -> dispatchCommand(playerName, cmd) }
        }
    }
}
