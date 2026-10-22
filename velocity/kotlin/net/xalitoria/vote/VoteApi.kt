package net.xalitoria.vote

import mcv.xalitoria.vote.XaliVote

actual fun isOnline(playerName: String): Boolean =
    XaliVote.INSTANCE.server.getPlayer(playerName).isPresent

actual fun dispatchCommand(playerName: String, command: String) {
    val player = XaliVote.INSTANCE.server.getPlayer(playerName).orElse(null) ?: return
    XaliVote.INSTANCE.server.commandManager.executeAsync(player, command.withPlayer(playerName))
}
