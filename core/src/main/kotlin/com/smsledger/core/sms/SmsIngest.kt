package com.smsledger.core.sms

import java.time.LocalDateTime

/** One SMS as read from the phone. [key] makes ingest idempotent across inbox scans and the receiver. */
data class RawSms(val key: String, val sender: String, val body: String, val at: LocalDateTime)

/** The user's answer to "Couldn't read", saved against the full template key (§9). */
data class LearnedTemplate(val key: String, val isTransaction: Boolean, val moneyIn: Boolean, val amountIndex: Int)

/** What ingest needs to know about the user's world. All of it comes from storage. */
data class IngestContext(
    /** Brand → kind, from discovery and later confirmations. */
    val senders: Map<String, SenderKind>,
    val mine: List<Account>,
    val notMine: List<Account>,
    val templates: Map<String, LearnedTemplate> = emptyMap(),
    /** SMS-backed ledger rows from the last 24 hours, for labels, duplicate alerts and self-transfer pairing. */
    val recent: List<Candidate> = emptyList(),
    /** Manual entries not yet merged with a bank SMS. */
    val manual: List<Candidate> = emptyList(),
)

/** What to do with one SMS. The app applies it; nothing here touches storage. */
sealed interface Outcome {
    /** Personal message, ignored sender, or an account marked "Not mine". */
    data object Ignore : Outcome

    /**
     * Stopped by the pre-filter or the account gate (logic.md §5, §8.0), a repeat alert (§11.2), or a
     * template the user marked "not a transaction" (reason null).
     */
    data class Skip(val reason: SkipReason?) : Outcome

    /** A merchant / payment-app SMS (or wallet-UPI notice) that names an existing payment (§7, §11.3). */
    data class Label(val txId: Long, val payee: String) : Outcome

    data class Add(
        val amountPaise: Long,
        val direction: Direction,
        val account: Account,
        val upiRef: String?,
        val counterparty: String?,
        /** A manual entry this bank SMS confirms (§13): merge instead of adding a second row. */
        val mergeWithManual: Long? = null,
        /** The other half of a self-transfer (§12). */
        val transfer: Pairing? = null,
    ) : Outcome

    /** Names an account of the user's but has no clear verb or amount: "Couldn't read" (§9). [account] when the digits resolve. */
    data class Unreadable(val account: Account?) : Outcome

    /**
     * Digits that fit several accounts, or a transaction-shaped message with no digits that could
     * not be placed with certainty (§8.0 "ambiguous" / "not sure"). The user picks, or says neither.
     */
    data class AskAccount(
        val brand: String,
        val digits: String?,
        val candidates: List<Account>,
        val amountPaise: Long,
        val direction: Direction,
        val counterparty: String?,
        val upiRef: String?,
    ) : Outcome

    /** A transaction-shaped message with unknown digits, or from an unconfirmed wallet: ask "Mine / Not mine", then re-ingest. */
    data class NewAccount(val account: Account) : Outcome
}

object SmsIngest {

    /** The decision tree of logic.md §10, for one message. */
    fun process(sms: RawSms, ctx: IngestContext): Outcome {
        val brand = SenderNormalizer.brand(sms.sender) ?: return Outcome.Ignore
        val kind = ctx.senders[brand] ?: SenderClassifier.classify(brand, listOf(sms.body))
        return when (kind) {
            SenderKind.Ignored -> Outcome.Ignore
            SenderKind.NonBank -> label(sms, brand, ctx)
            SenderKind.Wallet -> wallet(sms, brand, ctx)
            SenderKind.Bank -> bank(sms, brand, ctx)
        }
    }

    /** §7: a label-only sender can only name a payment that already exists; it never creates money. */
    private fun label(sms: RawSms, brand: String, ctx: IngestContext): Outcome {
        val parsed = TransactionParser.parse(sms.body) as? ParseResult.Parsed ?: return Outcome.Ignore
        val id = LabelMatcher.match(LabelSms(parsed.amountPaise, parsed.direction, sms.at, parsed.upiRef), ctx.recent) ?: return Outcome.Ignore
        return Outcome.Label(id, parsed.counterparty ?: brand)
    }

    private fun wallet(sms: RawSms, brand: String, ctx: IngestContext): Outcome {
        val account = Account(brand, Account.WALLET)
        if (account in ctx.notMine) return Outcome.Ignore
        TransactionParser.prefilter(sms.body)?.let { return Outcome.Skip(it) }
        val mine = account in ctx.mine
        if (mine) {
            learned(sms, ctx)?.let { (outcome, parsed) -> return outcome ?: add(sms, account, parsed!!, ctx) }
        }
        // §8.0: at a wallet the message must still be transaction-shaped — a verb and an amount.
        val kind = WalletRules.classify(sms.body)
        val amount = Patterns.transactionAmount(sms.body)
        if (kind == WalletKind.Unknown || amount == null) return Outcome.Skip(SkipReason.NoAccount)
        if (!mine) return Outcome.NewAccount(account)
        val direction = when (kind) {
            WalletKind.UpiLabel -> return label(sms, brand, ctx)
            WalletKind.TopUp, WalletKind.Credit -> Direction.Credit
            else -> Direction.Debit
        }
        val parsed = ParseResult.Parsed(amount, direction, null, Patterns.upiRef(sms.body), Patterns.counterparty(sms.body))
        return add(sms, account, parsed, ctx)
    }

    private fun bank(sms: RawSms, brand: String, ctx: IngestContext): Outcome {
        TransactionParser.prefilter(sms.body)?.let { return Outcome.Skip(it) }

        // A learned template answers "is this a transaction, and which number is the amount" (§9).
        val learned = learned(sms, ctx)
        learned?.first?.let { return it }
        val parsed: ParseResult.Parsed? = learned?.second ?: TransactionParser.parse(sms.body) as? ParseResult.Parsed

        // The account gate (§8.0): a message that names no account of the user's creates nothing.
        val runs = Patterns.allMaskedDigits(sms.body)
        if (runs.isEmpty()) {
            if (parsed == null || ctx.mine.isEmpty()) return Outcome.Skip(SkipReason.NoAccount)
            val atBank = ctx.mine.filter { it.brand == brand }
            val structured = parsed.upiRef != null || Patterns.hasBalance(sms.body)
            return if (atBank.size == 1 && structured) add(sms, atBank.single(), parsed, ctx) else ask(brand, null, atBank.ifEmpty { ctx.mine }, parsed)
        }
        // A message can name two accounts, the user's and the other party's: the user's own decides.
        val known = ctx.mine + ctx.notMine
        val matches = runs.map { it to AccountMatcher.match(brand, it, known) }
        val (digits, match) = matches.firstOrNull { (_, m) -> m is AccountMatch.Matched && m.account in ctx.mine }
            ?: matches.firstOrNull { (_, m) -> m is AccountMatch.Ambiguous && m.candidates.any { it in ctx.mine } }
            ?: matches.firstOrNull { (_, m) -> m !is AccountMatch.Unknown }
            ?: matches.first()
        return when (match) {
            is AccountMatch.Matched -> when {
                match.account in ctx.notMine -> Outcome.Ignore
                parsed == null -> Outcome.Unreadable(match.account)
                else -> add(sms, match.account, parsed, ctx)
            }
            is AccountMatch.Ambiguous -> when {
                match.candidates.none { it in ctx.mine } -> Outcome.Ignore
                parsed == null -> Outcome.Unreadable(null)
                else -> ask(brand, digits, match.candidates, parsed)
            }
            AccountMatch.Unknown -> if (parsed == null) Outcome.Skip(SkipReason.NoAccount) else Outcome.NewAccount(Account(brand, digits))
        }
    }

    /**
     * A learned template, if this message reduces to one: (Skip, null) for "not a transaction",
     * (null, parsed) with the learned amount and direction, (Unreadable, null) if the number is missing.
     */
    private fun learned(sms: RawSms, ctx: IngestContext): Pair<Outcome?, ParseResult.Parsed?>? {
        if (ctx.templates.isEmpty()) return null
        val template = Templatizer.template(sms.body)
        val answer = ctx.templates[template.key] ?: similar(template.key, ctx.templates) ?: return null
        if (!answer.isTransaction) return Outcome.Skip(null) to null
        val amount = Templatizer.amountAt(template, answer.amountIndex)?.takeIf { it > 0 } ?: return Outcome.Unreadable(null) to null
        val direction = if (answer.moneyIn) Direction.Credit else Direction.Debit
        return null to ParseResult.Parsed(amount, direction, Patterns.maskedDigits(sms.body), Patterns.upiRef(sms.body), Patterns.counterparty(sms.body))
    }

    /**
     * §9: the closest learned template sharing at least 80% of its words. The numbers must sit in the
     * same slots in both, so "the amount is the 2nd number" still points at the amount.
     */
    private fun similar(key: String, templates: Map<String, LearnedTemplate>): LearnedTemplate? {
        val words = key.lowercase().split(' ')
        val slots = Templatizer.slots(key)
        return templates.values
            .filter { Templatizer.slots(it.key) == slots }
            .map { it to overlap(words, it.key.lowercase().split(' ')) }
            .filter { it.second >= SmsDefaults.TEMPLATE_OVERLAP }
            .maxByOrNull { it.second }?.first
    }

    /** Words in common, counting repeats, over the longer word list. */
    private fun overlap(a: List<String>, b: List<String>): Double {
        val left = b.groupingBy { it }.eachCount().toMutableMap()
        var common = 0
        for (word in a) {
            val n = left[word] ?: 0
            if (n > 0) {
                left[word] = n - 1
                common++
            }
        }
        return common.toDouble() / maxOf(a.size, b.size)
    }

    private fun ask(brand: String, digits: String?, candidates: List<Account>, p: ParseResult.Parsed) =
        Outcome.AskAccount(brand, digits, candidates, p.amountPaise, p.direction, p.counterparty, p.upiRef)

    private fun add(sms: RawSms, account: Account, p: ParseResult.Parsed, ctx: IngestContext): Outcome {
        val me = Candidate(-1, p.amountPaise, p.direction, sms.at, account.key, p.upiRef)
        if (DuplicateAlert.isDuplicate(me, ctx.recent)) return Outcome.Skip(SkipReason.Duplicate)
        val merge = ManualMerger.match(me, ctx.manual)
        val transfer = if (merge == null) TransferPairer.find(me, ctx.recent) else null
        return Outcome.Add(p.amountPaise, p.direction, account, p.upiRef, p.counterparty, merge, transfer)
    }
}

/** An account found by discovery, with the latest message that shows it (shown as evidence, §8.2). */
data class FoundAccount(
    val account: Account,
    val lastAmountPaise: Long?,
    val lastDirection: Direction?,
    val lastAt: LocalDateTime,
    /** How many transaction messages name this account. */
    val messages: Int = 0,
)

data class DiscoveryResult(val senders: Map<String, SenderKind>, val accounts: List<FoundAccount>)

/**
 * First run (§7, §8.2): read every SMS once, decide what each sender is, and list the bank accounts
 * and wallets for the user to confirm. Nothing is stored here; message bodies stay in memory.
 */
object SmsDiscovery {
    fun discover(messages: List<RawSms>): DiscoveryResult {
        val byBrand = LinkedHashMap<String, MutableList<RawSms>>()
        messages.sortedBy { it.at }.forEach { sms ->
            val brand = SenderNormalizer.brand(sms.sender) ?: return@forEach
            byBrand.getOrPut(brand) { mutableListOf() } += sms
        }
        val senders = byBrand.mapValues { (brand, list) -> SenderClassifier.classify(brand, list.map { it.body }) }
        val found = byBrand.flatMap { (brand, list) ->
            when (senders[brand]) {
                SenderKind.Bank -> {
                    // Each message is read once: the digit runs of every transaction-shaped message.
                    val shaped = list.filter { TransactionParser.parse(it.body) is ParseResult.Parsed }.map { it to Patterns.allMaskedDigits(it.body) }
                    val seen = shaped.flatMap { it.second }.distinct()
                    // "821" and "4821" are one account written two ways: only the longer is listed.
                    seen.filter { digits -> seen.none { it.length > digits.length && it.endsWith(digits) } }.map { digits ->
                        val naming = shaped.filter { (_, runs) -> runs.any { digits.endsWith(it) } }
                        evidence(Account(brand, digits), naming.last().first, naming.size)
                    }
                }
                SenderKind.Wallet -> {
                    val shaped = list.filter { WalletRules.classify(it.body) != WalletKind.Unknown && Patterns.transactionAmount(it.body) != null }
                    listOf(evidence(Account(brand, Account.WALLET), shaped.lastOrNull() ?: list.last(), shaped.size))
                }
                else -> emptyList()
            }
        }
        // Only accounts the phone hears from again and again are put to the user. An inbox too small
        // for any account to get there (a new phone) falls back to everything found.
        val frequent = found.filter { it.messages >= SmsDefaults.ACCOUNT_MIN_MESSAGES }
        return DiscoveryResult(senders, frequent.ifEmpty { found })
    }

    private fun evidence(account: Account, sms: RawSms, messages: Int): FoundAccount {
        val parsed = TransactionParser.parse(sms.body) as? ParseResult.Parsed
        return FoundAccount(account, parsed?.amountPaise ?: Patterns.transactionAmount(sms.body), parsed?.direction, sms.at, messages)
    }
}
