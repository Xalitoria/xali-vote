package net.xalitoria.vote

/** Returns true if a player with this exact name is currently online on this platform. */
expect fun isOnline(playerName: String): Boolean

/** Runs [command] as the console, replacing `{player}` with [playerName]. No-op if the platform can't dispatch here. */
expect fun dispatchCommand(playerName: String, command: String)
