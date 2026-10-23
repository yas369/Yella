# Logic audit against the v0 spec (24 Oct 2026)

Checks the app against `sms-ledger-v0-features.md` section by section. "Preview" means it works on in-app sample data; real storage and SMS come with the plan tasks named.

**Where the rules live:** `core/src/main/kotlin/com/smsledger/core/` (pure Kotlin, unit-tested). The preview store (`ui/demo/DemoStore.kt`) and, later, the Room-backed app both call these rules.

## Fixed in this pass

| Spec | Bug found | Fix | Test |
|---|---|---|---|
| §12 Month view | Month total summed every transaction ever, not the current month | `LedgerMath.spentInMonth` (calendar month, debits only, not-daily included) | `LedgerMathTest`, `DemoStoreTest` |
| §10 / §11 "All like this" | Snackbar said all payments were tagged; only one row changed | `CategoryRules.apply` tags every debit from the same payee (case/space-insensitive) | `CategoryRulesTest`, `DemoStoreTest` |
| §11 Sticky payee tags | Tagging a payee wasn't remembered | Every tag becomes a payee rule; new manual spends and learned messages are pre-tagged | `DemoStoreTest` |
| §15 Needs you: New payee / Tag in one go | Picking a category only showed a message | Tags the payee's existing debits and saves the rule; card count is live | `DemoStoreTest` |
| §7 Teach a message | Said "₹1,450 spent" but added nothing | Adds the transaction (account, payee, date from the message) | `DemoStoreTest` |
| §4 Which account? | Picking an account did nothing | Adds the debit to the chosen account; "Neither" ignores it | manual |
| §9 Self-transfer pairing | "Self-transfer" did nothing | Both rows stay, both become transfers and stop counting | `DemoStoreTest` |
| §9 Lent | Marking lent didn't touch "Owed to you"; un-marking couldn't undo it | `LentBook.lend` / `cancelLend` | `LentBookTest`, `DemoStoreTest` |
| §9 Repayment | Money received from someone who owed you counted as income; no partial repayments | `LentBook.repay`: closes/reduces the debt, only the excess is income | `LentBookTest`, `DemoStoreTest` |
| §9 Write off | Removed the person outright | `LentBook.writeOff` closes the debt; lending again starts fresh | `LentBookTest` |
| §10 Not a transaction | Deleted the row | Row stays (dimmed, "Not counted") and leaves every total; can be counted again | `LedgerMathTest`, `DemoStoreTest` |
| §10 Edit every decision | Amount, direction and payee couldn't be edited | "Edit amount, direction or payee" in the edit sheet; a lent row's debt follows its new amount | manual |
| §10 Undo | Undo only restored one row | Every action snapshots rows, debts, rules and Needs-you items; Undo restores all of them | `DemoStoreTest` |
| §14 Search by amount | "250" matched ₹1,250 | Whole-rupee query matches that rupee amount; with paise, the exact amount | `HistoryMatchTest` |

## Already correct (re-verified)

| Spec | Check |
|---|---|
| §12 Daily limit | Debits only; uncategorised count; not-daily, credits, transfers and lent never count; "₹X over limit", never negative; amber at 80% |
| §12 / §9 Daily totals | Verified on device: lent ₹68 → spent ₹160 → ₹92, owed ₹2,500 → ₹2,568 |
| Money format | Indian grouping; totals rounded to whole rupees, single rows exact |

## Not built yet (planned, not bugs)

| Spec | Status | Task |
|---|---|---|
| §1 Room storage | Nothing is saved; the preview resets when the app restarts | T6 |
| §2 Modes | Mode switch is a flag; the SMS permission isn't requested yet | T9 |
| §3 Sender discovery, §4 account discovery | Sample accounts only | T3, T10 |
| §5 Wallet rules, §6 parsing, §7 template memory | Sample messages only; learned templates aren't stored or re-applied | T4, T5, T14, T16 |
| §8 Label matching, manual + SMS merge | Not started | T15, T18 |
| §4 / §10 Editing the account of a row | Needs real accounts | T10, T20 |
| §13 Limit alerts, lent reminder, battery prompt | Switches only; no notifications are sent | T23, T24 |
| Widget day rollover | Can show yesterday until the app opens | T27 |
