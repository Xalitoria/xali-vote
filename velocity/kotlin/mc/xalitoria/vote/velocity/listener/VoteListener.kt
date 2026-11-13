package mc.xalitoria.vote.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.vexsoftware.votifier.velocity.event.VotifierEvent
import kotlinx.coroutines.launch
import mc.xalitoria.vote.velocity.XaliVote

class VoteListener(private val plugin: XaliVote) {

    @Subscribe
    fun onVote(event: VotifierEvent) {
        val vote = event.vote
        plugin.scope.launch { plugin.flow.process(vote.username, vote.serviceName, vote.address) }
    }
}
