package com.smsledger.core.sms

/**
 * Regexes shared by discovery, parsing and templating. Written against common Indian bank formats;
 * accuracy on real messages is unmeasured until the corpus run (plan T2–T4).
 */
object Patterns {

    private val AMOUNT = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val BALANCE_BEFORE = Regex("""(?i)(?:bal(?:ance)?\.?|balance\s+is)\s*(?:is|of|:|-)?\s*$""")

    private val MASKED = listOf(
        Regex("""(?i)(?<![a-z])[x*]{2,}\s?(\d{3,4})\b"""),                                  // XX1234, **1234
        Regex("""(?i)\b(?:a/c|acct|account)\s*(?:no\.?\s*)?[:\-]?\s*\*(\d{3,4})\b"""),     // A/c *234
        Regex("""(?i)\bending(?:\s+(?:with|in))?\s*[:\-]?\s*[x*]*(\d{3,4})\b"""),          // card ending 4821
        Regex("""(?i)\b(?:a/c|acct|account)\s*(?:no\.?\s*)?[:\-]?\s*(\d{3,4})\b"""),       // A/c 1234
    )

    val VPA = Regex("""\b([a-zA-Z0-9][a-zA-Z0-9._\-]+@[a-zA-Z]{2,})\b""")
    private val PHONE = Regex("""(?<!\d)(?:\+?91[\- ]?)?([6-9]\d{9})(?!\d)""")
    private val UPI_REF = Regex("""(?i)(?:upi[\s/:]*(?:ref(?:erence)?|txn|id)?\.?\s*(?:no\.?)?\s*[:#\-]?\s*|\bref(?:erence)?\.?\s*(?:no\.?)?\s*[:#\-]?\s*|\brrn\s*[:#\-]?\s*)(\d{12})(?!\d)""")
    private val AT_MERCHANT = Regex("""(?i)\bat\s+([A-Za-z0-9][A-Za-z0-9&'.\- ]{0,40}?)(?=\s+(?:on|from|via|ref|using)\b|[.,]|\s*$)""")
    private val TO_PAYEE = Regex("""\b(?:to|towards)\s+([A-Z][A-Za-z0-9&'.\- ]{0,40}?)(?=\s+(?:on|from|via|ref|using|upi)\b|[.,]|\s*$)""")

    private val MONEY_WORDS = Regex("""(?i)\b(debited|credited|paid|spent|received|withdrawn|refund(?:ed)?)\b""")

    data class Amount(val range: IntRange, val paise: Long)

    /** Every Rs/INR/₹ amount, with whether it is a balance ("Avl bal Rs 9,000"). */
    fun amounts(body: String): List<Pair<Amount, Boolean>> = AMOUNT.findAll(body).map { m ->
        val number = m.groups[1]!!
        val isBalance = BALANCE_BEFORE.containsMatchIn(body.substring(0, m.range.first))
        Amount(number.range, toPaise(number.value)) to isBalance
    }.toList()

    /** First amount that isn't a balance. */
    fun transactionAmount(body: String): Long? = amounts(body).firstOrNull { !it.second }?.first?.paise

    fun hasAmount(body: String): Boolean = AMOUNT.containsMatchIn(body)

    /** Last 3–4 digits of a masked account or card (§4), or null. */
    fun maskedDigits(body: String): String? = MASKED.firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) }

    fun allMaskedDigits(body: String): List<String> =
        MASKED.flatMap { r -> r.findAll(body).map { it.range.first to it.groupValues[1] } }.sortedBy { it.first }.map { it.second }.distinct()

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
