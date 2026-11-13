package mc.xalitoria.vote.paper.listener

import com.vexsoftware.votifier.model.VotifierEvent
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
        plugin.flow.process(playerName, serviceName, address)
    }
}
