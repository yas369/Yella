package com.smsledger.core.sms

/**
 * Regexes shared by discovery, parsing and templating (logic.md §6). Written against common Indian
 * bank formats; accuracy on real messages is unmeasured until the corpus run (plan T2–T4).
 */
object Patterns {

    private val AMOUNT = Regex("""(?i)(?<![a-z])(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    /** "Rs 2L limit", "₹5 lakh": never a transaction amount, and a promo marker (§5, §6.1). */
    private val SHORTHAND = Regex("""(?i)(?<![a-z])(?:rs\.?|inr|₹)\s*[0-9.,]+\s?(?:k|l|lac|lakh|lakhs|cr|crore)\b""")

    /** The whole "Avl Limit: INR 57,888.30" clause, figure included (§6.1). */
    private val LIMIT_CLAUSE = Regex("""(?i)\b(?:avl|avail|available)\.?\s+(?:credit\s+)?(?:limit|lmt)\b\s*(?:is)?\s*:?\s*(?:rs\.?|inr|₹)?\s*[0-9][0-9,]*(?:\.[0-9]+)?""")

    /** A word this close before an amount means the amount is not the money that moved (§6.1). */
    private val EXCLUDE_CONTEXT = Regex("""(?i)(?<![a-z])(avl|available|bal|balance|limit|lmt|due|outstanding|min|upto|up to|offer|loan|emi|eligible|worth)\b""")
    private const val CONTEXT_CHARS = 20

    private val BALANCE = Regex("""(?i)\b(?:avl\.?\s*bal(?:ance)?|available\s*bal(?:ance)?|bal(?:ance)?)\b""")

    // §6.3: 3–12 digits after an account keyword, a bare mask (XX1234), or "ending 1234".
    private val ACCOUNT = Regex("""(?i)(?<![a-z])(?:a/c|acct|account|card|ac)\s*(?:no\.?)?\s*[:\-]?\s*(?:ending(?:\s+(?:with|in))?)?\s*[:\-]?\s*[x*•]*\s?([0-9]{3,12})\b""")
    private val MASK = Regex("""(?i)(?<![a-z0-9])[x*•]{2,}\s?([0-9]{3,12})\b""")
    private val ENDING = Regex("""(?i)\bending(?:\s+(?:with|in))?\s*[:\-]?\s*[x*•]*([0-9]{3,12})\b""")
    private val DATE = Regex("""(?<!\d)(?:\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4}|\d{4}[/\-]\d{1,2}[/\-]\d{1,2})(?!\d)""")

    val VPA = Regex("""\b([a-zA-Z0-9][a-zA-Z0-9._\-]+@[a-zA-Z]{2,})\b""")
    private val PHONE = Regex("""(?<!\d)(?:\+?91[\- ]?)?([6-9]\d{9})(?!\d)""")
    private val UPI_REF = Regex("""(?i)(?:upi[\s/:]*(?:ref(?:erence)?|txn|id)?\.?\s*(?:no\.?)?\s*[:#\-]?\s*|\bref(?:erence)?\.?\s*(?:no\.?)?\s*[:#\-]?\s*|\brrn\s*[:#\-]?\s*|\btxn\s*id\s*[:#.\-]?\s*)(\d{12})(?!\d)""")
    private val AT_MERCHANT = Regex("""(?i)\bat\s+([A-Za-z0-9][A-Za-z0-9&'.\- ]{0,40}?)(?=\s+(?:on|from|via|ref|using)\b|[.,]|\s*$)""")
    private val TO_PAYEE = Regex("""\b(?:to|towards)\s+([A-Z][A-Za-z0-9&'.\- ]{0,40}?)(?=\s+(?:on|from|via|ref|using|upi)\b|[.,]|\s*$)""")

    private val MONEY_WORDS = Regex("""(?i)\b(debited|credited|paid|spent|received|withdrawn|refund(?:ed)?)\b""")

    data class Amount(val range: IntRange, val paise: Long)

    /** Same length as [body], with every match of [regex] replaced by spaces so positions still line up. */
    private fun blank(body: String, regex: Regex): String = regex.replace(body) { " ".repeat(it.value.length) }

    /**
     * §6.1: amounts that can be the money that moved. Limit clauses and shorthand ("Rs 2L") are
     * removed first; an amount right after a balance / limit / due / offer word is left out. The
     * look-back never reaches past the previous amount, so "Avl Bal Rs 9,000. Rs 120 debited"
     * still reads the 120.
     */
    fun eligibleAmounts(body: String): List<Amount> {
        val text = blank(blank(body, LIMIT_CLAUSE), SHORTHAND)
        var previousEnd = 0
        val out = ArrayList<Amount>()
        for (m in AMOUNT.findAll(text)) {
            val from = maxOf(previousEnd, m.range.first - CONTEXT_CHARS)
            val excluded = EXCLUDE_CONTEXT.containsMatchIn(text.substring(from, m.range.first))
            previousEnd = m.range.last + 1
            val number = m.groups[1]!!
            val paise = toPaise(number.value)
            if (!excluded && paise > 0) out += Amount(number.range, paise)
        }
        return out
    }

    /** §6.1: the eligible amount closest to the first transaction verb; ties go to the earlier one. */
    fun transactionAmount(body: String, verbAt: Int? = TransactionParser.firstVerbAt(body)): Long? {
        val eligible = eligibleAmounts(body)
        if (verbAt == null) return eligible.firstOrNull()?.paise
        return eligible.minByOrNull { if (it.range.last < verbAt) verbAt - it.range.last else it.range.first - verbAt }?.paise
    }

    fun hasAmount(body: String): Boolean = AMOUNT.containsMatchIn(body)

    fun hasShorthand(body: String): Boolean = SHORTHAND.containsMatchIn(body)

    /** A balance phrase ("Avl bal", "balance"): structure that marks a real bank alert (§8.0). */
    fun hasBalance(body: String): Boolean = BALANCE.containsMatchIn(body)

    /** §8.1: digits only, leading zeros dropped; fewer than 3 is too short to identify an account. */
    fun cleanDigits(run: String): String? = run.filter { it.isDigit() }.trimStart('0').takeIf { it.length >= 3 }

    /** §8.1: one ends with the other. "8123" ~ "123", but not "8123" ~ "4123". */
    fun compatible(a: String, b: String): Boolean = a.endsWith(b) || b.endsWith(a)

    /** Every account digit run in the message, cleaned, in order of appearance. Runs inside a date are not accounts (§6.3). */
    fun allMaskedDigits(body: String): List<String> {
        val dates = DATE.findAll(body).map { it.range }.toList()
        return listOf(ACCOUNT, MASK, ENDING)
            .flatMap { regex -> regex.findAll(body).map { it.groups[1]!! } }
            .filter { run -> dates.none { run.range.first <= it.last && it.first <= run.range.last } }
            .sortedBy { it.range.first }
            .mapNotNull { cleanDigits(it.value) }
            .distinct()
    }

    /** The first account digit run (§6.3), cleaned, or null. */
    fun maskedDigits(body: String): String? = allMaskedDigits(body).firstOrNull()

    fun upiRef(body: String): String? = UPI_REF.find(body)?.groupValues?.get(1)

    /** VPA, then phone, then merchant ("at X", "to X"). */
    fun counterparty(body: String): String? =
        VPA.find(body)?.groupValues?.get(1)
            ?: PHONE.find(body)?.groupValues?.get(1)
            ?: AT_MERCHANT.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: TO_PAYEE.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    fun mentionsMoney(body: String): Boolean = hasAmount(body) || MONEY_WORDS.containsMatchIn(body)

    fun toPaise(number: String): Long {
        val clean = number.replace(",", "")
        val rupees = clean.substringBefore('.').ifEmpty { "0" }.toLong()
        val paise = clean.substringAfter('.', "").padEnd(2, '0').take(2).toLong()
        return rupees * 100 + paise
    }
}
