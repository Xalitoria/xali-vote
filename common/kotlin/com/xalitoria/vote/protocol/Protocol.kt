package com.xalitoria.vote.protocol

import com.xalitoria.vote.config.VoteConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What the proxy plugin and the servers tell each other.
 *
 * The database is always right; these messages only carry what the database does not hold: the settings the proxy shares, and
 * the news that a party started.
 */
sealed interface Message {
    /**
     * The settings the proxy shares with the servers: the vote links, whether they are added to the game's Server Links, and the
     * party. The servers use them in place of their own. Proxy to server.
     */
    data class Settings(
        val links: List<VoteConfig.Link>,
        val serverLinks: VoteConfig.ServerLinks,
        val party: VoteConfig.Party,
    ) : Message

    /** Vote party number [number] started where the vote was counted; every other server runs the party's commands for its players. Either way. */
    data class Party(val number: Long) : Message
}

/**
 * Seals a [Message] for the plugin channel `xalivote:main` and opens one that arrived.
 *
 * Anything a client can send to a plugin channel reaches the plugins on the server too, and a forged party would run console
 * commands, so each message travels signed: the 32-byte HMAC-SHA256 of the message, then the message. The key is Velocity's
 * forwarding secret, which the proxy and every server already share. The message carries the time it was made, so a copy of one
 * that is kept cannot be used later. A frame is at most [MAX_FRAME] bytes (Bukkit refuses a plugin message of more than 32 766).
 */
object Protocol {
    const val CHANNEL = "xalivote:main"
    const val MAX_FRAME = 30_000

    /** How far from the clock of the receiver the time of a message may be, in milliseconds. */
    const val MAX_SKEW_MILLIS = 60_000L

    /** The most links, so that [Message.Settings] always fits a frame. */
    const val MAX_LINKS = 40

    private const val MAC_BYTES = 32
    private const val VERSION = 1

    // The numbers are part of the wire format: only ever add to the end.
    private const val SETTINGS = 0
    private const val PARTY = 1

    /** Whether [frame] could be a sealed message, which is all the proxy asks before it passes the frame on. */
    fun isFrame(frame: ByteArray): Boolean = frame.size in (MAC_BYTES + 1)..MAX_FRAME

    /** [message] signed with [secret], made at [now] (milliseconds since the epoch); throws if it is too big for a frame. */
    fun seal(secret: String, message: Message, now: Long = System.currentTimeMillis()): ByteArray {
        val inner = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).apply {
                writeByte(VERSION)
                writeLong(now)
                write(encode(message))
            }
        }.toByteArray()
        val frame = ByteArrayOutputStream().also { DataOutputStream(it).apply { write(mac(secret, inner)); write(inner) } }.toByteArray()
        require(frame.size <= MAX_FRAME) { "A message has at most $MAX_FRAME bytes, and this one has ${frame.size}" }
        return frame
    }

    /**
     * The message in a [frame], read at [now]; null when it is unsigned, forged, signed with another secret, cut short, made by a
     * clock that is more than [MAX_SKEW_MILLIS] away, or of a kind this version does not know.
     */
    fun open(secret: String?, frame: ByteArray, now: Long = System.currentTimeMillis()): Message? {
        val secret = secret.orEmpty()
        if (secret.isBlank() || !isFrame(frame)) return null
        val input = DataInputStream(ByteArrayInputStream(frame))
        val mac = input.readNBytes(MAC_BYTES)
        val inner = input.readAllBytes()
        if (!MessageDigest.isEqual(mac, mac(secret, inner))) return null
        return try {
            DataInputStream(ByteArrayInputStream(inner)).run {
                if (readUnsignedByte() != VERSION) return null
                if (kotlin.math.abs(now - readLong()) > MAX_SKEW_MILLIS) return null
                decode(this)
            }
        } catch (e: IOException) {
            null
        }
    }

    /** [message] without a signature, to keep a copy of it on disk. */
    fun encode(message: Message): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).apply {
            when (message) {
                is Message.Settings -> {
                    writeByte(SETTINGS)
                    val links = message.links.take(MAX_LINKS)
                    writeInt(links.size)
                    links.forEach { writeUTF(it.name); writeUTF(it.url); writeUTF(it.description) }
                    writeBoolean(message.serverLinks.enabled)
                    writeUTF(message.serverLinks.prefix)
                    writeBoolean(message.party.enabled)
                    writeInt(message.party.votes)
                    writeStrings(message.party.commands)
                    writeStrings(message.party.once)
                }
                is Message.Party -> {
                    writeByte(PARTY)
                    writeLong(message.number)
                }
            }
        }
    }.toByteArray()

    /** The message [encode] wrote; null when it is of a kind this version does not know or is cut short. */
    fun decode(bytes: ByteArray): Message? = try {
        decode(DataInputStream(ByteArrayInputStream(bytes)))
    } catch (e: IOException) {
        null
    }

    private fun decode(input: DataInputStream): Message? = input.run {
        when (readUnsignedByte()) {
            SETTINGS -> {
                val count = readInt()
                if (count !in 0..MAX_LINKS) return null
                val links = List(count) { VoteConfig.Link(readUTF(), readUTF(), readUTF()) }
                val serverLinks = VoteConfig.ServerLinks(readBoolean(), readUTF())
                val party = VoteConfig.Party(readBoolean(), readInt(), readStrings(), readStrings())
                Message.Settings(links, serverLinks, party)
            }
            PARTY -> Message.Party(readLong())
            else -> null
        }
    }

    private fun DataOutputStream.writeStrings(strings: List<String>) {
        writeInt(strings.size)
        strings.forEach(::writeUTF)
    }

    private fun DataInputStream.readStrings(): List<String> {
        val count = readInt()
        if (count !in 0..1_000) throw IOException("Too many strings: $count")
        return List(count) { readUTF() }
    }

    // Whitespace around the secret is trimmed on both sides, since a secret file usually ends in a newline.
    private fun mac(secret: String, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256")
        .apply { init(SecretKeySpec(secret.trim().toByteArray(), "HmacSHA256")) }
        .doFinal(data)
}
