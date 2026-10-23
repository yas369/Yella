package com.smsledger.ui.demo

import com.smsledger.core.ledger.LentBook
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.DiscoveredAccount
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import java.time.LocalDate

/** Synthetic sample data for the design preview. No real messages, names or account numbers. */
object SampleData {

    val categories = listOf(
        Category("food", "Food"),
        Category("groceries", "Groceries"),
        Category("travel", "Travel"),
        Category("shopping", "Shopping"),
        Category("health", "Health"),
        Category("fun", "Fun"),
        Category("bills", "Bills", countsDaily = false),
        Category("rent", "Rent", countsDaily = false),
        Category("emi", "EMI", countsDaily = false),
    )

    val accounts = listOf(
        DiscoveredAccount("hdfc-4821", "HDFC Bank", "•• 4821", "Last: ₹249 debited · today"),
        DiscoveredAccount("cub-1234", "City Union Bank", "•• 1234", "Last: ₹500 debited · today"),
        DiscoveredAccount("sbi-9876", "SBI", "•• 9876", "Last: ₹12,000 credited · 3 days ago"),
        DiscoveredAccount("amazonpay", "Amazon Pay", "Wallet", "Last: ₹68 paid · today", isWallet = true),
    )

    val baseTransactions = listOf(
        Tx(1, "Rapido", "HDFC ••4821", "9:12 am", 0, 9_200, TxKind.Spend, "travel"),
        Tx(2, "Third Wave Coffee", "Amazon Pay", "11:40 am", 0, 6_800, TxKind.Spend, null),
        Tx(3, "Rahul S", "HDFC ••4821", "12:15 pm", 0, 100_000, TxKind.Lent, null),
        Tx(0, "To Amazon Pay", "CUB ••1234", "8:02 am", 0, 50_000, TxKind.Transfer, null),
        Tx(10, "Swiggy", "HDFC ••4821", "8:45 pm", 1, 41_800, TxKind.Spend, "food"),
        Tx(11, "DMart", "CUB ••1234", "6:10 pm", 1, 132_450, TxKind.Spend, "groceries"),
        Tx(12, "MYNTRA", "HDFC ••4821", "3:30 pm", 1, 129_900, TxKind.Spend, null),
        // Possible self-transfer pair (Needs you): counted as a spend and an income until the user pairs them.
        Tx(21, "Amazon Pay top-up", "CUB ••1234", "10:20 am", 1, 100_000, TxKind.Spend, null),
        Tx(22, "From CUB ••1234", "Amazon Pay", "10:22 am", 1, 99_500, TxKind.Income, null),
        Tx(13, "Apollo Pharmacy", "SBI ••9876", "10:05 am", 2, 48_600, TxKind.Spend, "health"),
        Tx(14, "Priya M", "SBI ••9876", "7:20 pm", 2, 40_000, TxKind.Income, null, note = "Repaid"),
        Tx(23, "Zomato", "HDFC ••4821", "9:10 pm", 2, 38_900, TxKind.Spend, null),
        Tx(15, "BESCOM", "SBI ••9876", "9:00 am", 3, 214_000, TxKind.Spend, "bills"),
        Tx(18, "PVR Cinemas", "HDFC ••4821", "9:40 pm", 4, 78_000, TxKind.Spend, "fun"),
        Tx(19, "Uber", "HDFC ••4821", "11:55 pm", 4, 31_200, TxKind.Spend, "travel"),
        Tx(20, "Blinkit", "HDFC ••4821", "7:15 pm", 5, 34_200, TxKind.Spend, null),
        Tx(24, "Zomato", "HDFC ••4821", "8:30 pm", 6, 45_600, TxKind.Spend, null),
        Tx(25, "Zomato", "Amazon Pay", "1:20 pm", 9, 27_300, TxKind.Spend, null),
        Tx(16, "ACME Payroll", "SBI ••9876", "10:00 am", 22, 8_500_000, TxKind.Income, null),
        Tx(17, "Rent · Mr. Iyer", "SBI ••9876", "9:30 am", 22, 1_800_000, TxKind.Spend, "rent"),
    )

    /** Extra spends used by the "Near limit" and "Over limit" preview scenarios. */
    val warningExtras = listOf(Tx(5, "Swiggy", "HDFC ••4821", "1:05 pm", 0, 24_900, TxKind.Spend, "food"))
    val overExtras = warningExtras + Tx(6, "DMart", "CUB ••1234", "6:30 pm", 0, 21_100, TxKind.Spend, "groceries")

    val needs = listOf(
        NeedsItem.Unreadable(
            101, "AX-HDFCBK",
            "Rs.1,450.00 spent on your HDFC Bank Card ending 4821 at RELIANCE SMART on 22-10-26. Avl bal Rs.23,180.55",
            account = "HDFC ••4821", payee = "RELIANCE SMART", daysAgo = 2,
        ),
        NeedsItem.Unreadable(
            102, "JD-CUBANK",
            "Your a/c XX1234 is debited for INR 2,000.00 towards UPI/829341027731. -City Union Bank",
            account = "CUB ••1234", payee = "UPI 829341027731", daysAgo = 0,
        ),
        NeedsItem.WhichAccount(103, "HDFC ••821", 76_000, daysAgo = 1, payee = "HDFC card spend", options = listOf("HDFC ••4821", "HDFC ••9821")),
        NeedsItem.PossibleTransfer(104, outTxId = 21, inTxId = 22, "₹1,000 out of CUB ••1234", "₹995 into Amazon Pay", "2 min apart · fee ₹5"),
        NeedsItem.NewPayee(105, txId = 12, payee = "MYNTRA", amountPaise = 129_900, whenLabel = "Yesterday"),
        NeedsItem.NewPayee(106, txId = 20, payee = "Blinkit", amountPaise = 34_200, whenLabel = "Mon"),
        NeedsItem.Bulk(107, "Zomato"),
    )

    /** VPA / phone per person; the real app keys debts by these (§9). */
    val handles = mapOf(
        "rahul s" to "rahul.s@okaxis",
        "priya m" to "+91 98•••••421",
        "arjun k" to "arjunk@ybl",
    )

    /** Owed to you: Rahul (today's lent row), Priya (part repaid), Arjun. */
    fun lentBook(today: LocalDate): LentBook {
        val book = LentBook()
            .lend("Rahul S", 100_000, today)
            .lend("Priya M", 160_000, today.minusDays(42))
            .lend("Arjun K", 30_000, today.minusDays(22))
        return book.repay("Priya M", 40_000, today.minusDays(2)).first
    }
}
