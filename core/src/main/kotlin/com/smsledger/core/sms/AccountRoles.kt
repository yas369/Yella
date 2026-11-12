package com.smsledger.core.sms

/** What kind of account a digit run names, from the words around it. Shown to the user; never changes how money is counted. */
enum class AccountKind { Bank, Card, Wallet }

/** One account number as written in a message. */
data class AccountRun(val digits: String, val range: IntRange, val kind: AccountKind)

/**
 * Whose account a number in a message is (logic.md §8.6). A bank alert often names two accounts:
 * the user's and the other party's ("debited from a/c XX4821 and credited to a/c XX7777"). Only
 * the user's own may ever become an account, be counted, or be asked about.
 *
 * The wording just before each number decides:
 *  - "beneficiary" / "payee" next to it → the other party's, always.
 *  - "your" → the user's.
 *  - otherwise the last direction word: money leaves **from** the user's account **to** someone
 *    else's when the message is a debit, and arrives **to / in** the user's **from** someone else's
 *    when it is a credit.
 *  - no clue → undecided, settled by the other numbers in the message.
 */
object AccountRoles {

    private enum class Role { Own, Other, Unknown }

    private const val LOOK_BACK = 60
    private const val LOOK_AHEAD = 30

    private val THIRD_PARTY = Regex("""(?i)\b(?:beneficiary|benef|payee|receiver|recipient)\b""")
    private val MARKER = Regex("""(?i)\b(your|from|towards|into|to|in|by)\b""")

    /**
     * The account numbers in [body] that are the user's own, in order of appearance. Empty when the
     * message names accounts but none is the user's. [direction] is the message's own direction
     * (null when it could not be read).
     */
    fun own(body: String, direction: Direction?): List<AccountRun> {
        val runs = Patterns.accountRuns(body)
        if (runs.isEmpty()) return emptyList()
        var previousEnd = 0
        val roles = runs.map { run ->
            val before = body.substring(maxOf(previousEnd, run.range.first - LOOK_BACK), run.range.first)
            val after = body.substring(run.range.last + 1, minOf(body.length, run.range.last + 1 + LOOK_AHEAD))
            previousEnd = run.range.last + 1
            role(before, after, direction)
        }
        val own = runs.filterIndexed { i, _ -> roles[i] == Role.Own }
        val undecided = runs.filterIndexed { i, _ -> roles[i] == Role.Unknown }
        return when {
            // Named outright: anything left undecided beside it is not needed.
            own.isNotEmpty() -> own
            // "A/c XX4821 debited … credited to a/c XX7777": the one not marked as someone else's.
            // With no clue at all, the first number is the account the alert is about.
            undecided.isNotEmpty() -> listOf(undecided.first())
            else -> emptyList()
        }
    }

    private fun role(before: String, after: String, direction: Direction?): Role {
        if (THIRD_PARTY.containsMatchIn(before.takeLast(LOOK_AHEAD)) || THIRD_PARTY.containsMatchIn(after)) return Role.Other
        val marker = MARKER.findAll(before).lastOrNull()?.value?.lowercase() ?: return Role.Unknown
        return when (marker) {
            "your" -> Role.Own
            "from" -> when (direction) {
                Direction.Debit -> Role.Own
                Direction.Credit -> Role.Other
                null -> Role.Unknown
            }
            "to", "towards", "into" -> when (direction) {
                Direction.Debit -> Role.Other
                Direction.Credit -> Role.Own
                null -> Role.Unknown
            }
            "in" -> if (direction == Direction.Credit) Role.Own else Role.Unknown
            "by" -> if (direction == Direction.Credit) Role.Other else Role.Unknown
            else -> Role.Unknown
        }
    }
}

/** Two discovered accounts whose last digits match, put to the user as "Same account?". Order does not matter. */
data class AccountPair(val a: Account, val b: Account) {
    fun has(account: Account) = a == account || b == account

    /** Stable text form for storage, the same whichever way round the pair was made. */
    val key: String get() = listOf(a.key, b.key).sorted().joinToString("\t")
}

/**
 * "Same account?" (logic.md §8.4): the same account can be written "XX821", "XX4821" or in full,
 * and can arrive under two sender names. Nothing is merged on a guess: every pair whose last digits
 * match is asked once, and the answer is kept.
 */
object AccountGroups {

    /** Pairs of bank accounts or cards where one number ends with the other (3 digits or more). */
    fun pairs(accounts: List<Account>): List<AccountPair> {
        val banks = accounts.filterNot { it.isWallet }
        val out = ArrayList<AccountPair>()
        for (i in banks.indices) for (j in i + 1 until banks.size) {
            if (Patterns.compatible(banks[i].digits, banks[j].digits)) out += AccountPair(banks[i], banks[j])
        }
        return out
    }

    /** What the user's "Same account" answers make of the discovered accounts. */
    data class Merged(
        /** One entry per real account, each standing for all its spellings. */
        val accounts: List<FoundAccount>,
        /** Every other spelling → the account that stands for it. */
        val aliases: Map<Account, Account>,
    )

    /**
     * Joins the accounts the user said are the same. The spelling with the most digits stands for
     * the group (then the one seen most), so the fullest number is the one kept.
     */
    fun merge(found: List<FoundAccount>, same: Collection<AccountPair>): Merged {
        val parent = HashMap<Account, Account>()
        fun root(a: Account): Account {
            var r = a
            while (true) r = parent[r]?.takeIf { it != r } ?: return r
        }
        found.forEach { parent[it.account] = it.account }
        same.forEach { pair ->
            if (pair.a in parent && pair.b in parent) parent[root(pair.a)] = root(pair.b)
        }
        val aliases = HashMap<Account, Account>()
        val merged = found.groupBy { root(it.account) }.values.map { group ->
            val lead = group.sortedWith(compareByDescending<FoundAccount> { it.account.digits.length }.thenByDescending { it.messages }).first()
            group.forEach { if (it.account != lead.account) aliases[it.account] = lead.account }
            val latest = group.maxBy { it.lastAt }
            lead.copy(
                lastAmountPaise = latest.lastAmountPaise,
                lastDirection = latest.lastDirection,
                lastAt = latest.lastAt,
                messages = group.sumOf { it.messages },
            )
        }
        return Merged(merged, aliases)
    }
}
