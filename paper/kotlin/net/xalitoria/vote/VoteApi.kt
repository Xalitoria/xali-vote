package net.xalitoria.vote

import mc.xalitoria.vote.XaliVote.Companion.INSTANCE

actual fun isOnline(playerName: String): Boolean =
    INSTANCE.server.getPlayerExact(playerName) != null

actual fun dispatchCommand(playerName: String, command: String) {
    val server = INSTANCE.server
    server.dispatchCommand(server.consoleSender, command.withPlayer(playerName))
}
