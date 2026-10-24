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

    val isWallet: Boolean get() = digits == WALLET

    /** "HDFC Bank ••4821", "Amazon Pay" (design.md §8: masks written as ••1234). */
    val label: String get() = if (isWallet) WalletRegistry.label(brand) else "${BankNames.name(brand)} ••$digits"

    /** Short form for rows: "HDFC ••4821". */
    val shortLabel: String get() = if (isWallet) WalletRegistry.label(brand) else "${BankNames.short(brand)} ••$digits"

    companion object {
        const val WALLET = "WALLET"

        fun fromKey(key: String): Account? = key.split('|').takeIf { it.size == 2 }?.let { Account(it[0], it[1]) }
    }
}

/** Display names for common bank sender brands. Unknown brands show as sent. */
object BankNames {
    private val NAMES = listOf(
        "HDFC" to ("HDFC Bank" to "HDFC"), "ICICI" to ("ICICI Bank" to "ICICI"), "SBI" to ("SBI" to "SBI"),
        "AXIS" to ("Axis Bank" to "Axis"), "KOTAK" to ("Kotak Bank" to "Kotak"), "CUBANK" to ("City Union Bank" to "CUB"),
        "IDFC" to ("IDFC First Bank" to "IDFC"), "YESBNK" to ("Yes Bank" to "Yes"), "PNB" to ("Punjab National Bank" to "PNB"),
        "BOB" to ("Bank of Baroda" to "BoB"), "CANBNK" to ("Canara Bank" to "Canara"), "INDUS" to ("IndusInd Bank" to "IndusInd"),
        "FEDBNK" to ("Federal Bank" to "Federal"), "UNIONB" to ("Union Bank" to "Union"), "IOB" to ("Indian Overseas Bank" to "IOB"),
    )

    private fun find(brand: String) = NAMES.firstOrNull { brand.uppercase().contains(it.first) }?.second

    fun name(brand: String): String = find(brand)?.first ?: brand
    fun short(brand: String): String = find(brand)?.second ?: brand
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
