import com.xalitoria.util.config.DatabaseConfig
import com.xalitoria.vote.config.RewardEntry
import com.xalitoria.vote.db.DatabaseManager
import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.VoteCountRepository
import com.xalitoria.vote.db.repository.VoteRepository
import com.xalitoria.vote.defaultRewardsConfig
import com.xalitoria.vote.resolveCommands
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Rewards {
    private val rewards = mapOf(
        "basic" to RewardEntry(every = 1, commands = ["basic {player}"], exclude = ["milestone"]),
        "milestone" to RewardEntry(after = 10, every = 5, commands = ["milestone {player}"]),
        "first" to RewardEntry(after = 1, every = 1_000_000, commands = ["first {player}"]),
    )

    @Test
    fun `an entry fires from after on, every so many votes`() {
        assertEquals(["basic {player}", "first {player}"], resolveCommands(1, rewards))
        assertEquals(["basic {player}"], resolveCommands(9, rewards))
        assertEquals(["milestone {player}"], resolveCommands(10, rewards))
        assertEquals(["basic {player}"], resolveCommands(11, rewards))
        assertEquals(["milestone {player}"], resolveCommands(15, rewards))
    }

    @Test
    fun `an every below one counts as one instead of dividing by zero`() {
        val zero = mapOf("a" to RewardEntry(every = 0, commands = ["a"]), "b" to RewardEntry(every = -3, commands = ["b"]))
        assertEquals(["a", "b"], resolveCommands(7, zero))
    }

    @Test
    fun `the defaults give nothing until commands are added`() {
        assertTrue((1L..30L).all { resolveCommands(it, defaultRewardsConfig()).isEmpty() })
    }
}

/** The vote counts and the queue of pending votes against a real SQLite file. */
class Storage {
    @BeforeTest
    fun connect() {
        val folder = Files.createTempDirectory("xalivote-test").toFile().also { it.deleteOnExit() }
        DatabaseManager.connect(folder, DatabaseConfig(tablePrefix = "xalivote_"))
    }

    @AfterTest
    fun disconnect() = DatabaseManager.close()

    @Test
    fun `each player counts their own votes from one`() = runBlocking {
        assertEquals(1, dbQuery { VoteCountRepository.increment("Alice") })
        assertEquals(2, dbQuery { VoteCountRepository.increment("Alice") })
        assertEquals(1, dbQuery { VoteCountRepository.increment("Bob") })
        assertEquals(3, dbQuery { VoteCountRepository.increment("Alice") })
    }

    @Test
    fun `votes that come at once get a number each`() = runBlocking {
        val numbers = (1..20).map { async { dbQuery { VoteCountRepository.increment("Alice") } } }.awaitAll()
        assertEquals((1L..20L).toList(), numbers.sorted())
    }

    @Test
    fun `pending votes are claimed once, oldest first`() = runBlocking {
        dbQuery {
            VoteRepository.save("Alice", "site", "1.2.3.4", 5)
            Thread.sleep(2)
            VoteRepository.save("Alice", "site", "1.2.3.4", 6)
            VoteRepository.save("Bob", "site", "1.2.3.4", 1)
        }
        assertTrue(dbQuery { VoteRepository.hasPending("Alice") })
        assertEquals([5L, 6L], dbQuery { VoteRepository.claimAll("Alice") })
        assertTrue(dbQuery { VoteRepository.claimAll("Alice") }.isEmpty())
        assertEquals([1L], dbQuery { VoteRepository.claimAll("Bob") })
    }
}
