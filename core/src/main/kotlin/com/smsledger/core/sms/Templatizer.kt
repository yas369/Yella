package com.smsledger.core.sms

/**
 * Template memory (§7). A message becomes a template by replacing the parts that change;
 * the **full** template is the key. [numbers] keeps every number in order so a learned answer
 * ("the amount is the 2nd number") applies to the next message with the same key.
 */
data class Template(val key: String, val numbers: List<NumberToken>)

data class NumberToken(val text: String, val range: IntRange)

object Templatizer {

    private val MONTH = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"

    private val TOKEN = Regex(
        listOf(
            """(?<date>\b\d{1,2}[\-/]\d{1,2}[\-/]\d{2,4}\b|\b\d{1,2}[\- ](?:$MONTH)[a-z]*[\- ]\d{2,4}\b)""",
            """(?<time>\b\d{1,2}:\d{2}(?::\d{2})?\b)""",
            """(?<vpa>\b[a-z0-9][a-z0-9._\-]+@[a-z]{2,}\b)""",
            """(?<masked>(?<![a-z])(?:[x*]{2,}|\*)\d{3,4}\b|(?<=ending )\d{3,4}\b|(?<=ending with )\d{3,4}\b|(?<=a/c )\d{3,4}\b)""",
            """(?<amt>(?<=(?:rs\.?|inr|₹)\s?)\d[\d,]*(?:\.\d{1,2})?|\d[\d,]*\.\d{1,2}\b)""",
            """(?<phone>(?<!\d)(?:\+?91)?[6-9]\d{9}(?!\d))""",
            """(?<num>\d+)""",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    fun template(body: String): Template {
        val numbers = mutableListOf<NumberToken>()
        val key = TOKEN.replace(body) { m ->
            val g = m.groups
            val placeholder = when {
                g["date"] != null -> "{DATE}"
                g["time"] != null -> "{TIME}"
                g["vpa"] != null -> "{VPA}"
                g["masked"] != null -> "{DIGITS}"
                g["amt"] != null -> "{AMT}"
                g["phone"] != null -> "{PHONE}"
                m.value.length >= 6 -> "{NUM}"
                else -> "{N}"
            }
            if (g["vpa"] == null && g["date"] == null && g["time"] == null) numbers += NumberToken(m.value, m.range)
            placeholder
        }.replace(Regex("""\s+"""), " ").trim()
        return Template(key, numbers)
    }

    /** The amount at a learned number position, or null if this message has fewer numbers. */
    fun amountAt(template: Template, index: Int): Long? =
        template.numbers.getOrNull(index)?.text?.filter { it.isDigit() || it == '.' || it == ',' }?.let { Patterns.toPaise(it) }
}
