package mc.xalitoria.vote.paper.listener

import kotlinx.coroutines.launch
import mc.xalitoria.vote.paper.XaliVote
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class JoinListener(private val plugin: XaliVote) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name
        plugin.scope.launch {
            plugin.flow.claim(playerName)
            // After the claim, so that what the player knows is what the database holds.
            plugin.board.refresh()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        plugin.board.forget(event.player.name)
    }
}
