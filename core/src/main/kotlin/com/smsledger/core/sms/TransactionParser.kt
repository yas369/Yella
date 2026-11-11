package com.smsledger.core.sms

enum class Direction { Debit, Credit }

/** Why a message was ignored (logic.md §2 `ignore_reason`). */
enum class SkipReason { Otp, NotCompleted, BalanceOnly, Promo, NoAccount, Duplicate }

sealed interface ParseResult {
    /** Transaction-shaped (§8.0): passed the pre-filter, has a completed verb and an eligible amount. */
    data class Parsed(
        val amountPaise: Long,
        val direction: Direction,
        val maskedDigits: String?,
        val upiRef: String?,
        /** VPA, phone or merchant name, when present. */
        val counterparty: String?,
    ) : ParseResult

    /** Stopped by the pre-filter (§5). Never counted, never shown as a problem. */
    data class Skipped(val reason: SkipReason) : ParseResult

    /** Passed the pre-filter but has no clear verb or amount. Whether it is asked about is the account gate's call (§8.0). */
    data object Unreadable : ParseResult
}

/**
 * Pre-filter (§5) and first-line regex extractor (§6). Passing here is not enough to become a
 * transaction: the account gate in [SmsIngest] still decides.
 */
object TransactionParser {

    private val OTP = Regex("""(?i)\b(otp|one[\s\-]time password|verification code)\b""")

    // §5 row 2: something that has not happened.
    private val NOT_COMPLETED = Regex(
        """(?i)\b(will be (?:auto[\s\-]?)?(?:debited|credited|deducted|charged)|request(?:ed)?|autopay set ?up|created|revoked|expired|""" +
            """failed|declined|scheduled|insufficient|unsuccessful|rejected|could not be processed|not (?:been )?debited)\b""",
    )
    private val MANDATE = Regex("""(?i)\b(mandate|autopay|auto[\s\-]pay|standing instruction)\b""")

    // §5 row 3: carries an amount but moves no money.
    private val EARMARKED = Regex("""(?i)\b(asba|is blocked in your|have received payment)\b""")
    private val DUE = Regex("""(?i)\b(due on|is due|overdue|minimum amount due|in arrears)\b""")
    private val CONVERT = Regex("""(?i)\bconvert""")
    private val EMI = Regex("""(?i)\b(?:smart)?emi\b""")
    private val VOUCHER = Regex("""(?i)\bvouchers?\b""")
    private val VOUCHER_WITH = Regex("""(?i)\b(received|rewards?|redemption)\b""")
    private val CHARGED = Regex("""(?i)\b(spent|debited|charged)\b""")

    // §5 row 5. Tune on the dataset.
    private val PROMO = Regex(
        """(?i)(\boffers?\b|\bpre[\s\-]?approved\b|\beligible\b|\bapply now\b|\bavail\b|\bupgrade\b|\bup ?to\b|\bloans?\b|\bemi\b|""" +
            """\breward points\b|\bt&c\b|\bclick\b|\bdownload\b)""",
    )

    // §6.2. "Strong" verbs are past forms that say money moved; "weak" ones are nouns that also
    // appear in reminders and offers ("payment of Rs 500 is due", "cashback offer").
    private val STRONG_DEBIT = Regex("""(?i)\b(debited|withdrawn|spent|purchased|paid|sent|deducted|transferred to)\b""")
    private val WEAK_DEBIT = Regex("""(?i)\b(purchase|payment of|txn of)\b""")
    private val STRONG_CREDIT = Regex("""(?i)\b(credited|received|deposited|refunded|reversed|added to|loaded)\b""")
    private val WEAK_CREDIT = Regex("""(?i)\b(refund|cashback)\b""")

    /** "will be debited", "to be paid", "not credited", "Have you paid…?": the verb is not in its completed form (§5). */
    private val NOT_DONE_BEFORE = Regex("""(?i)\b(?:will|would|shall|to|can|could|may|not|if|have you)\s+(?:be\s+|been\s+)?$""")

    private data class Verb(val at: Int, val direction: Direction, val strong: Boolean)

    private fun verbs(body: String): List<Verb> {
        val out = ArrayList<Verb>()
        fun collect(regex: Regex, direction: Direction, strong: Boolean) {
            for (m in regex.findAll(body)) {
                val before = body.substring(maxOf(0, m.range.first - 24), m.range.first)
                if (!NOT_DONE_BEFORE.containsMatchIn(before)) out += Verb(m.range.first, direction, strong)
            }
        }
        collect(STRONG_DEBIT, Direction.Debit, true)
        collect(STRONG_CREDIT, Direction.Credit, true)
        collect(WEAK_DEBIT, Direction.Debit, false)
        collect(WEAK_CREDIT, Direction.Credit, false)
        return out
    }

    /**
     * §6.2: the earliest completed verb wins, because a debit alert often ends "… credited to {payee}".
     * A strong verb outranks a weak one, so "Payment of Rs 5,000 received" is a credit.
     */
    private fun pick(verbs: List<Verb>): Verb? = verbs.filter { it.strong }.minByOrNull { it.at } ?: verbs.minByOrNull { it.at }

    fun firstVerbAt(body: String): Int? = pick(verbs(body))?.at

    private fun isPromo(body: String) = PROMO.containsMatchIn(body) || Patterns.hasShorthand(body)

    /** §5, in order; the first match wins. Null means the message goes on to the account gate. */
    fun prefilter(body: String): SkipReason? {
        if (OTP.containsMatchIn(body)) return SkipReason.Otp
        val verbs = verbs(body)
        val strong = verbs.any { it.strong }
        if (NOT_COMPLETED.containsMatchIn(body)) return SkipReason.NotCompleted
        // A mandate set-up has no completed verb; an executed mandate payment does, and counts.
        if (MANDATE.containsMatchIn(body) && !strong) return SkipReason.NotCompleted
        if (EARMARKED.containsMatchIn(body)) return SkipReason.NotCompleted
        if (DUE.containsMatchIn(body) && !strong) return SkipReason.NotCompleted
        if (CONVERT.containsMatchIn(body) && EMI.containsMatchIn(body) && verbs.none { it.strong && it.direction == Direction.Debit }) {
            return SkipReason.NotCompleted
        }
        if (VOUCHER.containsMatchIn(body) && VOUCHER_WITH.containsMatchIn(body) && !CHARGED.containsMatchIn(body)) return SkipReason.NotCompleted
        if (Patterns.hasBalance(body) && verbs.isEmpty()) return SkipReason.BalanceOnly
        if (isPromo(body) && verbs.isEmpty()) return SkipReason.Promo
        return null
    }

    fun parse(body: String): ParseResult {
        prefilter(body)?.let { return ParseResult.Skipped(it) }
        val verb = pick(verbs(body)) ?: return ParseResult.Unreadable
        // Promo wording with only a noun like "purchase" or "cashback" is not certain enough to count.
        if (!verb.strong && isPromo(body)) return ParseResult.Unreadable
        val amount = Patterns.transactionAmount(body, verb.at) ?: return ParseResult.Unreadable
        return ParseResult.Parsed(
            amountPaise = amount,
            direction = verb.direction,
            maskedDigits = Patterns.maskedDigits(body),
            upiRef = Patterns.upiRef(body),
            counterparty = Patterns.counterparty(body),
        )
    }
}

enum class WalletKind { BalanceSpend, UpiLabel, TopUp, Credit, Unknown }

/**
 * Wallet SMS (§11.3): paid from the wallet balance counts as spending; paid through the wallet's UPI
 * from a bank account is only a label (the bank sends its own debit); money added is a top-up,
 * which pairs with the bank debit as a self-transfer.
 */
object WalletRules {
    private val TOP_UP = Regex("""(?i)(added to (?:your )?(?:[a-z ]+ )?(?:wallet|balance)|top[\s\-]?up|money added|loaded)""")
    private val VIA_BANK = Regex("""(?i)(upi.*(?:bank|a/c|account)|(?:bank|a/c|account).*upi|from your [a-z ]*(?:bank|a/c|account))""")
    private val CREDIT = Regex("""(?i)\b(credited|received|refund(?:ed)?|cashback|reversed)\b""")
    private val DEBIT = Regex("""(?i)\b(paid|spent|debited|sent|deducted)\b""")

    fun classify(body: String): WalletKind = when {
        TOP_UP.containsMatchIn(body) -> WalletKind.TopUp
        VIA_BANK.containsMatchIn(body) -> WalletKind.UpiLabel
        CREDIT.containsMatchIn(body) -> WalletKind.Credit
        DEBIT.containsMatchIn(body) -> WalletKind.BalanceSpend
        else -> WalletKind.Unknown
    }
}
