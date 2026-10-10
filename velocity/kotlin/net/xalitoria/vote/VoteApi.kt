package net.xalitoria.vote

import mcv.xalitoria.vote.XaliVoteVelocity

actual fun isOnline(playerName: String): Boolean =
    XaliVoteVelocity.INSTANCE.server.getPlayer(playerName).isPresent

actual fun dispatchCommand(playerName: String, command: String) {
    val player = XaliVoteVelocity.INSTANCE.server.getPlayer(playerName).orElse(null) ?: return
    XaliVoteVelocity.INSTANCE.server.commandManager.executeAsync(player, command.withPlayer(playerName))
}
