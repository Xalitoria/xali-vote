import com.xalitoria.util.config.DatabaseConfig
import com.xalitoria.vote.VoteFlow
import com.xalitoria.vote.VoteHost
import com.xalitoria.vote.config.RewardEntry
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.db.DatabaseManager
import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.PartyRepository
import com.xalitoria.vote.db.repository.VoteCountRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The party counter and the leaderboard against a real SQLite file. */
class PartyCount {
    @BeforeTest
    fun connect() {
        val folder = Files.createTempDirectory("xalivote-party").toFile().also { it.deleteOnExit() }
        DatabaseManager.connect(folder, DatabaseConfig(tablePrefix = "xalivote_"))
    }

    @AfterTest
    fun disconnect() = DatabaseManager.close()

    @Test
    fun `a party starts every so many votes and the count starts over`() = runBlocking<Unit> {
        val results = (1..7).map { dbQuery { PartyRepository.record(3) } }
        assertEquals([1L, 2L, 0L, 1L, 2L, 0L, 1L], results.map { it.progress })
        assertEquals([0, 0, 1, 0, 0, 1, 0], results.map { it.started })
        assertEquals(2L, dbQuery { PartyRepository.status() }.parties)
    }

    @Test
    fun `votes that arrive at once start each party once`() = runBlocking<Unit> {
        val results = (1..40).map { async { dbQuery { PartyRepository.record(5) } } }.awaitAll()
        assertEquals(8, results.sumOf { it.started })
        val status = dbQuery { PartyRepository.status() }
        assertEquals(0L, status.progress)
        assertEquals(8L, status.parties)
    }

    @Test
    fun `lowering the votes of a party catches up without starting endlessly`() = runBlocking<Unit> {
        repeat(9) { dbQuery { PartyRepository.record(100) } }
        val result = dbQuery { PartyRepository.record(2) }
        assertEquals(5, result.started)
        assertEquals(5L, dbQuery { PartyRepository.status() }.parties)
        assertEquals(0L, result.progress)
    }

    @Test
    fun `the top is the players with the most votes, ties by name`() = runBlocking<Unit> {
        listOf("Bob", "Alice", "Bob", "Carol", "Alice", "Bob").forEach { dbQuery { VoteCountRepository.increment(it) } }
        val top = dbQuery { VoteCountRepository.top(2) }
        assertEquals(["Bob", "Alice"], top.map { it.name })
        assertEquals([1, 2], top.map { it.position })
        assertEquals([3L, 2L], top.map { it.votes })
        assertEquals(1L, dbQuery { VoteCountRepository.total("Carol") })
        assertEquals(0L, dbQuery { VoteCountRepository.total("Nobody") })
        assertEquals(["Alice", "Bob", "Carol"], dbQuery { VoteCountRepository.top(10) }.map { it.name }.sorted())
    }
}

/** What a vote does, on a host that only records. */
class Flow {
    private class Host(override var party: VoteConfig.Party, override val rewards: Map<String, RewardEntry> = mapOf()) : VoteHost {
        val online = mutableSetOf<String>()
        val ran = mutableListOf<String>()
        val started = mutableListOf<Long>()
        val totals = mutableMapOf<String, Long>()
        var players = listOf<String>()

        override fun isOnline(playerName: String) = playerName in online
        override fun partyPlayers() = players
        override fun console(command: String) { ran += command }
        override fun voucherCommand(id: String, amount: Int) = "give $id $amount {player}"
        override fun counted(playerName: String, total: Long) { totals[playerName] = total }
        override fun partyStarted(number: Long) { started += number }
        override fun info(message: String) {}
    }

    @BeforeTest
    fun connect() {
        val folder = Files.createTempDirectory("xalivote-flow").toFile().also { it.deleteOnExit() }
        DatabaseManager.connect(folder, DatabaseConfig(tablePrefix = "xalivote_"))
    }

    @AfterTest
    fun disconnect() = DatabaseManager.close()

    @Test
    fun `a vote for someone online is rewarded now, one for someone offline when they join`() = runBlocking<Unit> {
        val rewards = mapOf("a" to RewardEntry(commands = ["say hi {player}"], vouchers = mapOf("crate" to 2)))
        val host = Host(VoteConfig.Party(), rewards).also { it.online += "Alice" }
        val flow = VoteFlow(host)
        flow.process("Alice", "site", "")
        assertEquals(["say hi Alice", "give crate 2 Alice"], host.ran)

        host.ran.clear()
        flow.process("Bob", "site", "")
        assertTrue(host.ran.isEmpty())
        flow.claim("Bob")
        assertEquals(["say hi Bob", "give crate 2 Bob"], host.ran)
        assertEquals(1L, host.totals["Bob"])
    }

    @Test
    fun `a party runs the once commands once and the commands for every player`() = runBlocking<Unit> {
        val party = VoteConfig.Party(enabled = true, votes = 2, commands = ["gift {player} {party}"], once = ["say party {party} of {votes}"])
        val host = Host(party).also { it.players = ["Alice", "Bob"] }
        val flow = VoteFlow(host)
        flow.process("Alice", "site", "")
        assertTrue(host.ran.isEmpty())
        flow.process("Bob", "site", "")
        assertEquals(["say party 1 of 2", "gift Alice 1", "gift Bob 1"], host.ran)
        assertEquals([1L], host.started)
    }

    @Test
    fun `a party that another server started runs the commands only`() = runBlocking<Unit> {
        val party = VoteConfig.Party(enabled = true, votes = 2, commands = ["gift {player}"], once = ["say once"])
        val host = Host(party).also { it.players = ["Alice"] }
        VoteFlow(host).joinParty(4)
        assertEquals(["gift Alice"], host.ran)
        host.party = party.copy(enabled = false)
        host.ran.clear()
        VoteFlow(host).joinParty(5)
        assertTrue(host.ran.isEmpty())
    }

    @Test
    fun `no party is counted while it is off`() = runBlocking<Unit> {
        val host = Host(VoteConfig.Party(enabled = false, votes = 1, commands = ["x"]))
        repeat(3) { VoteFlow(host).process("Alice", "site", "") }
        assertEquals(0L, dbQuery { PartyRepository.status() }.progress)
    }
}
