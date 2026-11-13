package mc.xalitoria.vote.paper.board

import com.xalitoria.util.kotlin.parseDuration
import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.PartyRepository
import com.xalitoria.vote.db.repository.VoteCountRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mc.xalitoria.vote.paper.XaliVote
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * What is answered from memory: the top voters, how far the party is, and the votes of the players who are online. A menu, a
 * placeholder on a scoreboard and the commands read these, so none of them waits for the database. It reads them again every
 * `top.refresh`, which is how votes that another server counted show up, and straight away for a vote counted here.
 */
class Board(private val plugin: XaliVote) {
    @Volatile
    var top: List<VoteCountRepository.Place> = []
        private set

    @Volatile
    var party = PartyRepository.Status(0, 0)
        private set

    private val totals = ConcurrentHashMap<String, Long>()

    /** The votes of [name] if they are online and known; otherwise 0. */
    fun votes(name: String): Long = totals[name.lowercase()] ?: 0L

    fun counted(name: String, total: Long) {
        totals[name.lowercase()] = total
        scheduleRefresh()
    }

    fun forget(name: String) {
        totals.remove(name.lowercase())
    }

    fun start() {
        plugin.scope.launch {
            while (isActive) {
                refresh()
                val every = runCatching { parseDuration("top.refresh", plugin.voteConfig.top.refresh) }.getOrNull()
                delay((every ?: 60.seconds).coerceAtLeast(5.seconds))
            }
        }
    }

    /** Reads the places, the party and the players who are online again. */
    suspend fun refresh() {
        try {
            val names = plugin.server.onlinePlayers.map { it.name }
            val size = plugin.voteConfig.top.size.coerceIn(1, 100)
            val [places, status, held] = dbQuery {
                Triple(VoteCountRepository.top(size), PartyRepository.status(), names.associateWith { VoteCountRepository.total(it) })
            }
            top = places
            party = status
            held.forEach { totals[it.key.lowercase()] = it.value }
        } catch (e: Exception) {
            plugin.logger.warning("Could not read the votes: ${e.message}")
        }
    }

    /** A vote that was just counted changes the places, so they are read soon, once for however many come together. */
    private val pending = java.util.concurrent.atomic.AtomicBoolean()

    private fun scheduleRefresh() {
        if (!pending.compareAndSet(false, true)) return
        plugin.scope.launch {
            delay(2.seconds)
            pending.set(false)
            refresh()
        }
    }
}
