package com.smsledger.core.sms

import java.time.LocalDateTime

/** One SMS as read from the phone. [key] makes ingest idempotent across inbox scans and the receiver. */
data class RawSms(val key: String, val sender: String, val body: String, val at: LocalDateTime)

/** The user's answer to "Couldn't read", saved against the full template key (§7). */
data class LearnedTemplate(val key: String, val isTransaction: Boolean, val moneyIn: Boolean, val amountIndex: Int)

/** What ingest needs to know about the user's world. All of it comes from storage. */
data class IngestContext(
    /** Brand → kind, from discovery and later confirmations. */
    val senders: Map<String, SenderKind>,
    val mine: List<Account>,
    val notMine: List<Account>,
    val templates: Map<String, LearnedTemplate> = emptyMap(),
    /** Recent SMS-backed ledger rows, for labels and self-transfer pairing. */
    val recent: List<Candidate> = emptyList(),
    /** Manual entries not yet merged with a bank SMS. */
    val manual: List<Candidate> = emptyList(),
)

/** What to do with one SMS. The app applies it; nothing here touches storage. */
sealed interface Outcome {
    /** Personal message, ignored sender, or an account marked "Not mine". */
    data object Ignore : Outcome

    /** Not a completed transaction (§6), or a template the user marked "not a transaction" (reason null). */
    data class Skip(val reason: SkipReason?) : Outcome

    /** A merchant / payment-app SMS (or wallet-UPI notice) that names an existing payment (§8). */
    data class Label(val txId: Long, val payee: String) : Outcome

    data class Add(
        val amountPaise: Long,
        val direction: Direction,
        val account: Account,
        val upiRef: String?,
        val counterparty: String?,
        /** A manual entry this bank SMS confirms (§8 default): merge instead of adding a second row. */
        val mergeWithManual: Long? = null,
        /** The other half of a self-transfer (§9). */
        val transfer: Pairing? = null,
    ) : Outcome

    /** From a confirmed sender but unreadable: "Couldn't read" (§7). [account] when the digits resolve. */
    data class Unreadable(val account: Account?) : Outcome

    /** Three digits that fit several accounts, or no digits at a multi-account bank (§4). */
    data class AskAccount(
        val brand: String,
        val digits: String?,
        val candidates: List<Account>,
        val amountPaise: Long,
        val direction: Direction,
        val counterparty: String?,
        val upiRef: String?,
    ) : Outcome

    /** Unknown digits at a known bank, or an unconfirmed wallet: ask "Mine / Not mine", then re-ingest. */
    data class NewAccount(val account: Account) : Outcome
}

object SmsIngest {

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

    /** §8: a non-bank SMS can only name a payment that already exists. */
    private fun label(sms: RawSms, brand: String, ctx: IngestContext): Outcome {
        val parsed = TransactionParser.parse(sms.body) as? ParseResult.Parsed ?: return Outcome.Ignore
        val id = LabelMatcher.match(LabelSms(parsed.amountPaise, parsed.direction, sms.at, parsed.upiRef), ctx.recent) ?: return Outcome.Ignore
        return Outcome.Label(id, parsed.counterparty ?: brand)
    }

    private fun wallet(sms: RawSms, brand: String, ctx: IngestContext): Outcome {
        val account = Account(brand, Account.WALLET)
        if (account in ctx.notMine) return Outcome.Ignore
        if (account !in ctx.mine) return Outcome.NewAccount(account)
        learned(sms, ctx)?.let { (outcome, parsed) ->
            return outcome ?: add(sms, account, parsed!!, ctx)
        }
        val direction = when (WalletRules.classify(sms.body)) {
            WalletKind.UpiLabel -> return label(sms, brand, ctx)
            WalletKind.TopUp, WalletKind.Credit -> Direction.Credit
            WalletKind.BalanceSpend -> Direction.Debit
            WalletKind.Unknown -> return Outcome.Unreadable(account)
        }
        val amount = Patterns.transactionAmount(sms.body) ?: return Outcome.Unreadable(account)
        val parsed = ParseResult.Parsed(amount, direction, null, Patterns.upiRef(sms.body), Patterns.counterparty(sms.body))
        return add(sms, account, parsed, ctx)
    }

    private fun bank(sms: RawSms, brand: String, ctx: IngestContext): Outcome {
        val learned = learned(sms, ctx)
        val parsed: ParseResult.Parsed = when {
            learned != null -> learned.first?.let { return it } ?: learned.second!!
            else -> when (val p = TransactionParser.parse(sms.body)) {
                is ParseResult.Skipped -> return Outcome.Skip(p.reason)
                is ParseResult.Unreadable -> return Outcome.Unreadable(resolveQuietly(brand, Patterns.maskedDigits(sms.body), ctx))
                is ParseResult.Parsed -> p
            }
        }
        val digits = parsed.maskedDigits
        val all = ctx.mine + ctx.notMine
        val account: Account = if (digits == null) {
            val atBank = ctx.mine.filter { it.brand == brand }
            when (atBank.size) {
                1 -> atBank.single()
                0 -> return Outcome.Unreadable(null)
                else -> return ask(brand, null, atBank, parsed)
            }
        } else {
            when (val match = AccountMatcher.match(brand, digits, all)) {
                is AccountMatch.Matched -> if (match.account in ctx.notMine) return Outcome.Ignore else match.account
                is AccountMatch.Ambiguous -> {
                    val mineOnly = match.candidates.filter { it in ctx.mine }
                    if (mineOnly.isEmpty()) return Outcome.Ignore
                    return ask(brand, digits, match.candidates, parsed)
                }
                AccountMatch.Unknown -> return Outcome.NewAccount(Account(brand, digits))
            }
        }
        return add(sms, account, parsed, ctx)
    }

    /**
     * A learned template, if this message reduces to one: (Skip, null) for "not a transaction",
     * (null, parsed) with the learned amount and direction, (Unreadable, null) if the number is missing.
     */
    private fun learned(sms: RawSms, ctx: IngestContext): Pair<Outcome?, ParseResult.Parsed?>? {
        if (ctx.templates.isEmpty()) return null
        val template = Templatizer.template(sms.body)
        val answer = ctx.templates[template.key] ?: return null
        if (!answer.isTransaction) return Outcome.Skip(null) to null
        val amount = Templatizer.amountAt(template, answer.amountIndex) ?: return Outcome.Unreadable(null) to null
        val direction = if (answer.moneyIn) Direction.Credit else Direction.Debit
        return null to ParseResult.Parsed(amount, direction, Patterns.maskedDigits(sms.body), Patterns.upiRef(sms.body), Patterns.counterparty(sms.body))
    }

    private fun ask(brand: String, digits: String?, candidates: List<Account>, p: ParseResult.Parsed) =
        Outcome.AskAccount(brand, digits, candidates, p.amountPaise, p.direction, p.counterparty, p.upiRef)

    private fun resolveQuietly(brand: String, digits: String?, ctx: IngestContext): Account? {
        if (digits == null) return ctx.mine.singleOrNull { it.brand == brand }
        return (AccountMatcher.match(brand, digits, ctx.mine) as? AccountMatch.Matched)?.account
    }

    private fun add(sms: RawSms, account: Account, p: ParseResult.Parsed, ctx: IngestContext): Outcome.Add {
        val me = Candidate(-1, p.amountPaise, p.direction, sms.at, account.key, p.upiRef)
        val merge = ManualMerger.match(me, ctx.manual)
        val transfer = if (merge == null) TransferPairer.find(me, ctx.recent) else null
        return Outcome.Add(p.amountPaise, p.direction, account, p.upiRef, p.counterparty, merge, transfer)
    }
}

/** An account found by discovery, with the latest message that shows it (shown as evidence, §4). */
data class FoundAccount(val account: Account, val lastAmountPaise: Long?, val lastDirection: Direction?, val lastAt: LocalDateTime)

data class DiscoveryResult(val senders: Map<String, SenderKind>, val accounts: List<FoundAccount>)

/**
 * First run (§3, §4): read every SMS once, decide what each sender is, and list the bank accounts
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
        val accounts = byBrand.flatMap { (brand, list) ->
            when (senders[brand]) {
                SenderKind.Bank -> SenderClassifier.accounts(brand, list.map { it.body }).map { digits ->
                    val last = list.last { Patterns.allMaskedDigits(it.body).contains(digits) }
                    evidence(Account(brand, digits), last)
                }
                SenderKind.Wallet -> listOf(evidence(Account(brand, Account.WALLET), list.last()))
                else -> emptyList()
            }
        }
        return DiscoveryResult(senders, accounts)
    }

    private fun evidence(account: Account, sms: RawSms): FoundAccount {
        val parsed = TransactionParser.parse(sms.body) as? ParseResult.Parsed
        return FoundAccount(account, parsed?.amountPaise ?: Patterns.transactionAmount(sms.body), parsed?.direction, sms.at)
    }
}
