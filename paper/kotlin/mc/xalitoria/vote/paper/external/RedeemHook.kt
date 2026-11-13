package mc.xalitoria.vote.paper.external

import mc.xalitoria.vote.paper.XaliVote

/**
 * XaliRedeem's side of a reward. XaliRedeem has no API to compile against: its `/xaliredeem admin give` is the way in, and the console
 * may run it, so a reward's `vouchers` are turned into that command. This class touches none of its classes; it only knows whether
 * the plugin is there and what a voucher's id may look like, so that a voucher can never add a second command to the line.
 *
 * `rewards.yml`: `vouchers: { vote-crate: 2 }` gives two of XaliRedeem's `vote-crate`. For `/xaliredeem give` to see the player they
 * must be known to XaliRedeem's database, which a player who is voting is, or joins.
 */
class RedeemHook(private val plugin: XaliVote) {
    /** Whether XaliRedeem has enabled. It loads before this plugin when it is installed. */
    val present: Boolean get() = plugin.server.pluginManager.isPluginEnabled(PLUGIN)

    fun command(id: String, amount: Int): String? {
        if (!present) return null
        if (!VOUCHER.matches(id)) {
            plugin.logger.warning("Voucher '$id' in rewards.yml is not a name XaliRedeem can have (lower case letters, digits, _ and -), so it is not given")
            return null
        }
        return "xaliredeem admin give {player} $id $amount"
    }

    /** The vouchers that the rewards name, for the warning at start and reload when nothing can give them. */
    fun warnIfMissing() {
        if (present) return
        val named = plugin.rewardsConfig.filterValues { it.vouchers.isNotEmpty() }.keys
        if (named.isNotEmpty()) plugin.logger.warning("rewards.yml gives vouchers in ${named.joinToString()}, but XaliRedeem is not installed, so they are left out")
    }

    companion object {
        const val PLUGIN = "XaliRedeem"
        private val VOUCHER = Regex("[a-z0-9_-]+")
    }
}
