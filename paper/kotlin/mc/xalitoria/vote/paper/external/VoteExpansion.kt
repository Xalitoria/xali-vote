package mc.xalitoria.vote.paper.external

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import mc.xalitoria.vote.paper.XaliVote
import org.bukkit.OfflinePlayer

/**
 * `%xalivote_...%`, answered from memory (what is known of the players who are online), never from the database, so they are safe on a
 * scoreboard that is drawn every tick. They are also what XaliRedeem's `placeholder` condition reads, so a voucher can ask for votes.
 *
 * - `votes`: the player's votes in all. 0 for someone who is not online.
 * - `top_name_<n>`, `top_votes_<n>`: place `<n>` (from 1) of the top voters. Empty past the last.
 * - `party_progress`, `party_needed`, `party_remaining`, `parties`: the party's count, the votes that make one, those still missing and
 *   how many have been held. `party_enabled` is `true` or `false`.
 * - `links`: how many vote links there are.
 */
class VoteExpansion(private val plugin: XaliVote) : PlaceholderExpansion() {
    override fun getIdentifier() = "xalivote"
    override fun getAuthor() = "audizian"
    override fun getVersion() = plugin.pluginMeta.version
    override fun persist() = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val parts = params.lowercase().split('_')
        val party = plugin.party
        val board = plugin.board
        return when (parts[0]) {
            "votes" -> (player?.name?.let(board::votes) ?: 0L).toString()
            "links" -> plugin.links.size.toString()
            "parties" -> board.party.parties.toString()
            "party" -> when (parts.getOrNull(1)) {
                "progress" -> board.party.progress.toString()
                "needed" -> party.votes.toString()
                "remaining" -> (party.votes - board.party.progress).coerceAtLeast(0).toString()
                "enabled" -> party.enabled.toString()
                else -> null
            }
            "top" -> {
                val place = parts.getOrNull(2)?.toIntOrNull()?.let { board.top.getOrNull(it - 1) } ?: return ""
                when (parts.getOrNull(1)) {
                    "name" -> place.name
                    "votes" -> place.votes.toString()
                    else -> null
                }
            }
            else -> null
        }
    }
}
