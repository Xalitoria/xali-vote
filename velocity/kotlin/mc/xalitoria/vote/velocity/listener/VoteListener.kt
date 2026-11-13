package mc.xalitoria.vote.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.vexsoftware.votifier.velocity.event.VotifierEvent
import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.VoteCountRepository
import com.xalitoria.vote.db.repository.VoteRepository
import com.xalitoria.vote.resolveCommands
import kotlinx.coroutines.launch
import mc.xalitoria.vote.velocity.XaliVote

class VoteListener(private val plugin: XaliVote) {

    @Subscribe
    fun onVote(event: VotifierEvent) {
        val vote = event.vote
        plugin.scope.launch {
            val voteNumber = dbQuery { VoteCountRepository.increment(vote.username) }
            val commands = resolveCommands(voteNumber, plugin.rewardsConfig)
            if (plugin.isOnline(vote.username)) {
                commands.forEach { cmd -> plugin.dispatchCommand(vote.username, cmd) }
            } else {
                dbQuery { VoteRepository.save(vote.username, vote.serviceName, vote.address, voteNumber) }
            }
            plugin.logger.info("Vote #$voteNumber: ${vote.username} from ${vote.serviceName}")
        }
    }
}
