package com.smsledger.core.money

/** Editing rules for the on-screen number pad. Text is plain rupees, e.g. "250.5". */
object AmountInput {
    private const val MAX_RUPEE_DIGITS = 7
    private const val MAX_DECIMALS = 2

    fun press(text: String, key: Char): String {
        val dot = text.indexOf('.')
        return when {
            key == '.' -> when {
                dot >= 0 -> text
                text.isEmpty() -> "0."
                else -> "$text."
            }
            !key.isDigit() -> text
            dot >= 0 -> if (text.length - dot - 1 >= MAX_DECIMALS) text else text + key
            text == "0" -> key.toString()
            text.length >= MAX_RUPEE_DIGITS -> text
            else -> text + key
        }
    }

    fun backspace(text: String): String = text.dropLast(1)

    fun toPaise(text: String): Long {
        if (text.isEmpty()) return 0
        val rupees = text.substringBefore('.').ifEmpty { "0" }.toLong()
        val paise = text.substringAfter('.', "").padEnd(MAX_DECIMALS, '0').take(MAX_DECIMALS).toLong()
        return rupees * 100 + paise
    }
}
