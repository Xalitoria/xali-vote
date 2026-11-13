import com.xalitoria.vote.config.RewardEntry
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.protocol.ForwardingSecret
import com.xalitoria.vote.protocol.Message
import com.xalitoria.vote.protocol.Protocol
import com.xalitoria.vote.resolveCommands
import com.xalitoria.vote.usableLinks
import com.xalitoria.vote.webAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Links {
    @Test
    fun `only http and https addresses with a host are usable`() {
        assertNotNull(webAddress("https://example.com/vote?u=1"))
        assertNotNull(webAddress(" http://example.com "))
        assertNull(webAddress("javascript:alert(1)"))
        assertNull(webAddress("file:///etc/passwd"))
        assertNull(webAddress("https://"))
        assertNull(webAddress("not a url"))
    }

    @Test
    fun `a link without a name or a usable address is left out and reported`() {
        val bad = mutableListOf<String>()
        val links = usableLinks(
            [
                VoteConfig.Link("Good", " https://a.example/vote "),
                VoteConfig.Link("", "https://b.example"),
                VoteConfig.Link("Odd", "ftp://c.example"),
            ],
        ) { bad += it.name }
        assertEquals(["Good"], links.map { it.name })
        assertEquals("https://a.example/vote", links.single().url)
        assertEquals(["", "Odd"], bad)
    }
}

class Sharing {
    private val secret = "s3cret\n"
    private val settings = Message.Settings(
        [VoteConfig.Link("Site", "https://a.example/vote", "Every day"), VoteConfig.Link("Other", "https://b.example")],
        VoteConfig.ServerLinks(true, "Vote: "),
        VoteConfig.Party(true, 30, ["give {player} diamond"], ["say party!"]),
    )

    @Test
    fun `settings and a party survive the trip`() {
        assertEquals(settings, Protocol.open(secret, Protocol.seal(secret, settings)))
        assertEquals(Message.Party(7), Protocol.open(secret, Protocol.seal(secret, Message.Party(7))))
    }

    @Test
    fun `a frame signed with another secret, changed, old or cut short is turned away`() {
        val frame = Protocol.seal(secret, Message.Party(1), now = 1_000_000)
        assertNull(Protocol.open("another", frame, now = 1_000_000))
        assertNull(Protocol.open(null, frame, now = 1_000_000))
        assertNull(Protocol.open(secret, frame.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }, now = 1_000_000))
        assertNull(Protocol.open(secret, frame, now = 1_000_000 + Protocol.MAX_SKEW_MILLIS + 1))
        assertNull(Protocol.open(secret, frame.copyOf(10), now = 1_000_000))
        assertNotNull(Protocol.open(secret, frame, now = 1_000_000 + Protocol.MAX_SKEW_MILLIS))
    }

    @Test
    fun `more links than fit are cut, and a frame that is still too big is refused`() {
        val many = settings.copy(links = List(60) { VoteConfig.Link("Site $it", "https://a.example/$it") })
        assertEquals(Protocol.MAX_LINKS, (Protocol.open(secret, Protocol.seal(secret, many)) as Message.Settings).links.size)
        val huge = settings.copy(party = VoteConfig.Party(commands = List(900) { "x".repeat(60) }))
        assertFailsWith<IllegalArgumentException> { Protocol.seal(secret, huge) }
    }

    @Test
    fun `a saved copy decodes to the same settings`() {
        assertEquals(settings, Protocol.decode(Protocol.encode(settings)))
        assertNull(Protocol.decode(byteArrayOf(9)))
        assertNull(Protocol.decode(byteArrayOf()))
    }

    @Test
    fun `a server is behind Velocity when paper-global enables forwarding and has a secret`() {
        val folder = Files.createTempDirectory("xalivote-server")
        assertEquals("", ForwardingSecret.ofServer(folder))
        Files.createDirectories(folder.resolve("config"))
        Files.writeString(folder.resolve("config/paper-global.yml"), "proxies:\n  velocity:\n    enabled: false\n    secret: abc\n")
        assertEquals("", ForwardingSecret.ofServer(folder))
        Files.writeString(folder.resolve("config/paper-global.yml"), "other: 1\nproxies:\n  velocity:\n    enabled: true\n    secret: abc\n")
        assertEquals("abc", ForwardingSecret.ofServer(folder))
    }

    @Test
    fun `the proxy reads the secret from the file velocity toml names`() {
        val folder = Files.createTempDirectory("xalivote-proxy")
        assertNull(ForwardingSecret.ofProxy(folder).takeIf { System.getenv("VELOCITY_FORWARDING_SECRET") == null })
        Files.writeString(folder.resolve("forwarding.secret"), "plain\n")
        if (System.getenv("VELOCITY_FORWARDING_SECRET") == null) {
            assertEquals("plain\n", ForwardingSecret.ofProxy(folder))
            Files.writeString(folder.resolve("velocity.toml"), "forwarding-secret-file = \"other.secret\"\n")
            Files.writeString(folder.resolve("other.secret"), "named")
            assertEquals("named", ForwardingSecret.ofProxy(folder))
        }
    }
}

class Vouchers {
    @Test
    fun `vouchers are turned into commands only when something can give them`() {
        val rewards = mapOf("a" to RewardEntry(commands = ["say hi"], vouchers = mapOf("crate" to 2, "none" to 0)))
        assertEquals(["say hi"], resolveCommands(1, rewards))
        assertEquals(["say hi", "give crate 2"], resolveCommands(1, rewards) { id, n -> "give $id $n" })
        assertEquals(["say hi"], resolveCommands(1, rewards) { _, _ -> null })
    }
}
