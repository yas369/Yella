package com.smsledger.core.sms

/** Sender brand from an SMS address (§3): `AD-CUBANK-S` → `CUBANK`. Phone numbers are people → null. */
object SenderNormalizer {
    private val PHONE = Regex("""^\+?\d[\d\s\-]{6,}$""")
    private val OPERATOR = Regex("""^[A-Z]{2}-([A-Z0-9]{3,})(?:-[A-Z])?$""")

    fun brand(address: String): String? {
        val s = address.trim().uppercase()
        if (PHONE.matches(s)) return null
        return OPERATOR.matchEntire(s)?.groupValues?.get(1) ?: s
    }
}

enum class SenderKind { Bank, Wallet, NonBank, Ignored }

/**
 * Wallet brands are known by name (§5). DLT header names vary by operator, so these are tokens
 * matched inside the brand. Unverified against real headers until the corpus run.
 */
object WalletRegistry {
    private val TOKENS = listOf("AMAZONPAY", "AMZNPY", "AMZPAY", "APAYIN", "PAYTM", "PYTMWL", "MOBIKW", "MBKWIK", "FCHRGE", "FRECHG")

    fun isWallet(brand: String): Boolean = TOKENS.any { brand.uppercase().contains(it) }

    /** "AMAZONPAY" → "Amazon Pay" style label for the wallet account. */
    fun label(brand: String): String = when {
        brand.contains("AMZ") || brand.contains("AMAZON") || brand.contains("APAY") -> "Amazon Pay"
        brand.contains("PAYTM") || brand.contains("PYTM") -> "Paytm Wallet"
        brand.contains("MOBIKW") || brand.contains("MBKWIK") -> "MobiKwik"
        brand.contains("FCHRGE") || brand.contains("FRECHG") -> "Freecharge"
        else -> brand
    }
}

/** Decides what a sender is from its messages (§3), and which accounts a bank sender has (§4). */
object SenderClassifier {

    /** A bank needs masked account digits *together with* an amount; money words alone are not enough. */
    fun classify(brand: String, bodies: List<String>): SenderKind = when {
        WalletRegistry.isWallet(brand) -> SenderKind.Wallet
        bodies.any { Patterns.maskedDigits(it) != null && Patterns.hasAmount(it) } -> SenderKind.Bank
        bodies.any { Patterns.mentionsMoney(it) } -> SenderKind.NonBank
        else -> SenderKind.Ignored
    }

    /** Distinct masked digits seen with an amount, in order of first appearance. */
    fun accounts(brand: String, bodies: List<String>): List<String> =
        bodies.filter { Patterns.hasAmount(it) }.flatMap { Patterns.allMaskedDigits(it) }.distinct()
}

/** An account the user confirmed as theirs: bank brand + masked digits, or a wallet keyed by brand. */
data class Account(val brand: String, val digits: String) {
    /** Stable key, e.g. `HDFCBK|4821`, `AMAZONPAY|WALLET`. */
    val key: String get() = "$brand|$digits"
}

sealed interface AccountMatch {
    data class Matched(val account: Account) : AccountMatch
    data class Ambiguous(val candidates: List<Account>) : AccountMatch
    data object Unknown : AccountMatch
}

/** §4 digit matching: 4 digits match directly; 3 digits only when exactly one account at that bank fits. */
object AccountMatcher {
    fun match(brand: String, digits: String, accounts: List<Account>): AccountMatch {
        val atBank = accounts.filter { it.brand == brand }
        atBank.firstOrNull { it.digits == digits }?.let { return AccountMatch.Matched(it) }
        val fits = atBank.filter { acc ->
            (digits.length == 3 && acc.digits.endsWith(digits)) || (acc.digits.length == 3 && digits.endsWith(acc.digits))
        }
        return when (fits.size) {
            0 -> AccountMatch.Unknown
            1 -> AccountMatch.Matched(fits.single())
            else -> AccountMatch.Ambiguous(fits)
        }
    }
}
