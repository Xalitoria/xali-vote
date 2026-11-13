package mc.xalitoria.vote.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import kotlinx.coroutines.launch
import mc.xalitoria.vote.velocity.XaliVote

class JoinListener(private val plugin: XaliVote) {

    @Subscribe
    fun onLogin(event: PostLoginEvent) {
        val playerName = event.player.username
        plugin.scope.launch { plugin.flow.claim(playerName) }
    }
}
