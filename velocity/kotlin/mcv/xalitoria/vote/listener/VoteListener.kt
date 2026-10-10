package mcv.xalitoria.vote.listener

import com.vexsoftware.votifier.velocity.event.VotifierEvent
import com.velocitypowered.api.event.Subscribe
import kotlinx.coroutines.launch
import net.xalitoria.vote.db.DatabaseManager.dbQuery
import net.xalitoria.vote.db.repository.VoteCountRepository
import net.xalitoria.vote.db.repository.VoteRepository
import net.xalitoria.vote.dispatchCommand
import net.xalitoria.vote.isOnline
import net.xalitoria.vote.resolveCommands
import mcv.xalitoria.vote.XaliVoteVelocity

class VoteListener(private val plugin: XaliVoteVelocity) {

    @Subscribe
    fun onVote(event: VotifierEvent) {
        val vote = event.vote
        plugin.scope.launch {
            val voteNumber = dbQuery { VoteCountRepository.increment(vote.username) }
            val commands = resolveCommands(voteNumber, plugin.rewardsConfig)
            if (isOnline(vote.username)) {
                commands.forEach { cmd -> dispatchCommand(vote.username, cmd) }
            } else {
                dbQuery { VoteRepository.save(vote.username, vote.serviceName, vote.address, voteNumber) }
            }
            plugin.logger.info("Vote #$voteNumber: ${vote.username} from ${vote.serviceName}")
        }
    }
}
