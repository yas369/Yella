package com.smsledger.core.sms

enum class Direction { Debit, Credit }

enum class SkipReason { Otp, Request, Failed, Future, Mandate, BalanceOnly }

sealed interface ParseResult {
    data class Parsed(
        val amountPaise: Long,
        val direction: Direction,
        val maskedDigits: String?,
        val upiRef: String?,
        /** VPA, phone or merchant name, when present. */
        val counterparty: String?,
    ) : ParseResult

    /** Not a completed transaction (§6). Never counted, never shown as a problem. */
    data class Skipped(val reason: SkipReason) : ParseResult

    /** From a confirmed sender but not readable: goes to "Couldn't read" (§7). */
    data object Unreadable : ParseResult
}

/**
 * First-line regex extractor (§6). Skips anything that isn't a completed transaction, then finds
 * amount, direction, account digits, UPI reference and counterparty. Anything it can't read is
 * [ParseResult.Unreadable], never dropped.
 */
object TransactionParser {

    private val OTP = Regex("""(?i)\b(otp|one[\s\-]time password|verification code)\b""")
    private val REQUEST = Regex("""(?i)\b(has requested|requested money|collect request|payment request)\b""")
    private val FAILED = Regex("""(?i)\b(declined|failed|unsuccessful|rejected|could not be processed|not debited)\b""")
    private val FUTURE = Regex("""(?i)\b(will be (?:debited|deducted|charged|auto[\s\-]?debited)|is scheduled|scheduled (?:on|for)|is due|due on|upcoming)\b""")
    private val MANDATE = Regex("""(?i)\b(mandate|autopay|auto[\s\-]pay|standing instruction)\b""")
    private val DEBIT = Regex("""(?i)\b(debited|debit|spent|paid|withdrawn|sent|deducted|purchase)\b""")
    private val CREDIT = Regex("""(?i)\b(credited|received|deposited|refund(?:ed)?|reversed)\b""")
    private val BALANCE = Regex("""(?i)\b(avl\.?\s*bal|available balance|balance is|bal(?:ance)?\s*:)""")

    fun parse(body: String): ParseResult {
        if (OTP.containsMatchIn(body)) return ParseResult.Skipped(SkipReason.Otp)
        if (REQUEST.containsMatchIn(body)) return ParseResult.Skipped(SkipReason.Request)
        if (FAILED.containsMatchIn(body)) return ParseResult.Skipped(SkipReason.Failed)
        if (FUTURE.containsMatchIn(body)) return ParseResult.Skipped(SkipReason.Future)

        val debitAt = DEBIT.find(body)?.range?.first
        val creditAt = CREDIT.find(body)?.range?.first
        // A mandate *set-up* has no completed-debit verb; an executed mandate payment does and counts.
        if (MANDATE.containsMatchIn(body) && debitAt == null) return ParseResult.Skipped(SkipReason.Mandate)

        val direction = when {
            debitAt != null && (creditAt == null || debitAt < creditAt) -> Direction.Debit
            creditAt != null -> Direction.Credit
            else -> null
        }
        if (direction == null) {
            return if (BALANCE.containsMatchIn(body)) ParseResult.Skipped(SkipReason.BalanceOnly) else ParseResult.Unreadable
        }
        val amount = Patterns.transactionAmount(body) ?: return ParseResult.Unreadable
        return ParseResult.Parsed(
            amountPaise = amount,
            direction = direction,
            maskedDigits = Patterns.maskedDigits(body),
            upiRef = Patterns.upiRef(body),
            counterparty = Patterns.counterparty(body),
        )
    }
}

enum class WalletKind { BalanceSpend, UpiLabel, TopUp, Credit, Unknown }

/**
 * Wallet SMS (§5): paid from the wallet balance counts as spending; paid through the wallet's UPI
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
