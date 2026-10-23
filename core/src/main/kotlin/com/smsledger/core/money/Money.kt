package com.smsledger.core.money

import kotlin.math.absoluteValue

/**
 * Formats an amount in paise as Indian rupees: `₹1,23,456`, or `₹250.50` when paise are non-zero.
 * Grouping is 3 digits, then 2 digits at a time (lakh / crore).
 */
fun formatRupees(paise: Long): String {
    val abs = paise.absoluteValue
    val rupees = groupIndian((abs / 100).toString())
    val fraction = abs % 100
    val sign = if (paise < 0) "-" else ""
    return if (fraction == 0L) "$sign₹$rupees" else "$sign₹$rupees.${fraction.toString().padStart(2, '0')}"
}

private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    return head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
}
