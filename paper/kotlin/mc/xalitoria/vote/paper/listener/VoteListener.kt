package mc.xalitoria.vote.paper.listener

import com.vexsoftware.votifier.model.VotifierEvent
import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.VoteCountRepository
import com.xalitoria.vote.db.repository.VoteRepository
import com.xalitoria.vote.resolveCommands
import kotlinx.coroutines.launch
import mc.xalitoria.vote.paper.XaliVote
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class VoteListener(private val plugin: XaliVote) : Listener {

    @EventHandler
    fun onVote(event: VotifierEvent) {
        val vote = event.vote
        plugin.scope.launch { processVote(vote.username, vote.serviceName, vote.address) }
    }

    internal suspend fun processVote(playerName: String, serviceName: String, address: String) {
        val voteNumber = dbQuery { VoteCountRepository.increment(playerName) }
        val commands = resolveCommands(voteNumber, plugin.rewardsConfig)
        if (plugin.isOnline(playerName)) {
            commands.forEach { plugin.dispatchCommand(playerName, it) }
        } else {
            dbQuery { VoteRepository.save(playerName, serviceName, address, voteNumber) }
        }
        plugin.logger.info("Vote #$voteNumber: $playerName from $serviceName")
    }
}
