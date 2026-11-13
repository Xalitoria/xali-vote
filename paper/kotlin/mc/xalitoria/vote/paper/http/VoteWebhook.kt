package mc.xalitoria.vote.paper.http

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mc.xalitoria.vote.paper.XaliVote
import mc.xalitoria.vote.paper.listener.VoteListener
import java.security.MessageDigest

class VoteWebhook(private val plugin: XaliVote, private val voteListener: VoteListener) {
    @Serializable
    data class Payload(
        val username: String,
        val serviceName: String,
        val address: String = "",
        val secret: String = "",
    )

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start(port: Int) {
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) { json() }
            routing {
                post("/vote") {
                    val payload = runCatching { call.receive<Payload>() }.getOrElse {
                        call.respond(HttpStatusCode.BadRequest, "Invalid payload")
                        return@post
                    }
                    val expectedSecret = plugin.voteConfig.webhook.secret
                    if (expectedSecret.isNotEmpty() && !sameSecret(payload.secret, expectedSecret)) {
                        call.respond(HttpStatusCode.Unauthorized, "Bad secret")
                        return@post
                    }
                    processVote(payload.username, payload.serviceName, payload.address)
                    call.respond(HttpStatusCode.OK, "Vote queued")
                }
            }
        }.start(wait = false)
    }

    private fun processVote(playerName: String, serviceName: String, address: String) {
        plugin.scope.launch { voteListener.processVote(playerName, serviceName, address) }
    }

    /** Compared in constant time, so the time a refusal takes does not tell how much of a guess was right. */
    private fun sameSecret(given: String, expected: String): Boolean =
        MessageDigest.isEqual(given.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))

    fun stop() {
        server?.stop(1_000L, 5_000L)
        server = null
    }
}
