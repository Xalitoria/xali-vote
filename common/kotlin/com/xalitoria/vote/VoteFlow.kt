package com.xalitoria.vote

import com.xalitoria.vote.config.RewardEntry
import com.xalitoria.vote.config.VoteConfig
import com.xalitoria.vote.db.DatabaseManager.dbQuery
import com.xalitoria.vote.db.repository.PartyRepository
import com.xalitoria.vote.db.repository.VoteCountRepository
import com.xalitoria.vote.db.repository.VoteRepository

/** What [VoteFlow] needs of the platform it runs on. Everything here may be asked from any thread. */
interface VoteHost {
    /** The rewards, as they are now. */
    val rewards: Map<String, RewardEntry>

    /** The party, as it is now: the proxy's where the proxy shares it, else this configuration's. */
    val party: VoteConfig.Party

    fun isOnline(playerName: String): Boolean

    /** The players the party's `commands` run for here. A proxy that leaves them to the servers answers none. */
    fun partyPlayers(): List<String>

    /** Runs [command] as the console, which has `{player}` filled in already. */
    fun console(command: String)

    /** The command that gives [amount] of voucher [id], or null where nothing can. */
    fun voucherCommand(id: String, amount: Int): String?

    /** A player now has [total] votes, which a platform keeps for what it answers from memory. */
    fun counted(playerName: String, total: Long) {}

    /** Party number [number] started here, which a platform tells the others. */
    fun partyStarted(number: Long) {}

    fun info(message: String)
}

/** What happens to a vote, the same on the proxy and on a server. */
class VoteFlow(private val host: VoteHost) {
    /**
     * Counts a vote for [playerName], gives the rewards now if they are online and else queues them for when they join, and counts it
     * toward the party. Returns the player's number of votes.
     */
    suspend fun process(playerName: String, serviceName: String, address: String): Long {
        val party = host.party
        val [number, counted] = dbQuery {
            val number = VoteCountRepository.increment(playerName)
            number to (if (party.enabled) PartyRepository.record(party.votes) else null)
        }
        host.counted(playerName, number)
        if (host.isOnline(playerName)) {
            resolve(number).forEach { dispatch(playerName, it) }
        } else {
            dbQuery { VoteRepository.save(playerName, serviceName, address, number) }
        }
        host.info("Vote #$number: $playerName from $serviceName")
        if (counted != null && counted.started > 0) {
            // The numbers of the parties this vote set off, which end with the last.
            for (index in 0 until counted.started) startParty(counted.lastNumber - counted.started + 1 + index, party)
        }
        return number
    }

    /** Gives the rewards of the votes that were queued for [playerName] while they were offline. */
    suspend fun claim(playerName: String) {
        val numbers = dbQuery { VoteRepository.claimAll(playerName) }
        if (numbers.isEmpty()) return
        host.info("Dispensing rewards for ${numbers.size} pending vote(s) to $playerName")
        numbers.flatMap(::resolve).forEach { dispatch(playerName, it) }
    }

    /** Runs the commands of party number [number] for the players here: what a server does when another one started it. */
    fun joinParty(number: Long) {
        val party = host.party
        if (!party.enabled) return
        runCommands(number, party)
    }

    private fun startParty(number: Long, party: VoteConfig.Party) {
        host.info("Vote party #$number: ${party.votes} votes counted")
        party.once.forEach { host.console(fill(it, number, party)) }
        runCommands(number, party)
        host.partyStarted(number)
    }

    private fun runCommands(number: Long, party: VoteConfig.Party) {
        for (player in host.partyPlayers()) party.commands.forEach { host.console(fill(it, number, party).withPlayer(player)) }
    }

    private fun fill(command: String, number: Long, party: VoteConfig.Party) =
        command.replace("{party}", number.toString()).replace("{votes}", party.votes.toString())

    private fun resolve(number: Long): List<String> = resolveCommands(number, host.rewards, host::voucherCommand)

    private fun dispatch(playerName: String, command: String) = host.console(command.withPlayer(playerName))
}
