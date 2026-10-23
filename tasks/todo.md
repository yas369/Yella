# SMS Ledger v0: Task List

Plan and rationale: [`tasks/plan.md`](plan.md). Spec section numbers are written §N.

**Standard commands** (defined in T1):
- Core tests: `./gradlew :core:test`
- App unit tests: `./gradlew :app:testDebugUnitTest`
- Instrumented tests: `./gradlew :app:connectedDebugAndroidTest`
- Build: `./gradlew assembleDebug`
- Corpus report: `./gradlew :tools:corpus:run --args="<report> <path-to-export.xml>"`

**Definition of done for every task:** tests pass, build is clean, no raw SMS body is logged, and no corpus data is committed.

---

## Phase 0: Foundation and policy risk

### T0: Play Store SMS policy pre-check and declaration draft
**Description:** Read the current Google Play SMS/Call Log policy and the "SMS-based money management" exception. Confirm the v0 design fits it (SMS is the core feature, on-device only, no sharing). Draft the Permissions Declaration Form answers and a privacy policy outline. List what the review needs, such as a demo video and an in-app prominent disclosure.
**Acceptance criteria:**
- [ ] `docs/play-policy.md` lists each policy requirement and how the app meets it
- [ ] Draft declaration answers and a privacy policy outline exist
- [ ] Any design change the policy forces is written down and fed back into this plan
**Verification:**
- [ ] Manual check: a human reviews `docs/play-policy.md`
**Dependencies:** None
**Files:** `docs/play-policy.md`, `docs/privacy-policy-draft.md`
**Scope:** S

### T1: Gradle project skeleton
**Description:** Create the Android project with three modules: `app` (Compose, Material 3), `core` (Kotlin JVM library) and `tools/corpus` (Kotlin JVM application that depends on `core`). Use a version catalog, set minSdk and targetSdk, add `.gitignore` entries that exclude `*.xml` exports under `data/`, and `git init`.
**Acceptance criteria:**
- [x] `./gradlew assembleDebug` builds an APK that launches to a blank Compose screen
- [x] `./gradlew :core:test` runs one sample test
- [x] `./gradlew :tools:corpus:run --args="hello"` prints output
**Verification:**
- [x] Build succeeds, tests pass, the app launches on an emulator
**Dependencies:** None
**Files:** `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `core/build.gradle.kts`, `tools/corpus/build.gradle.kts`, `.gitignore`
**Scope:** M
**Done:** Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21; compileSdk 37 (current Compose needs it), targetSdk 36, minSdk 26. Sample test = `formatRupees` (Indian grouping), 6 tests.

### T2: Corpus harness: stream the XML export and print sender stats
**Description:** Write a StAX streaming reader for the SMS XML export that never loads the whole file into memory, mapping each message to a `RawSms(id, sender, body, timestamp)` in `core`. Add a `senders` report: message count per raw sender address, top N.
**Acceptance criteria:**
- [ ] Processes the full ~400 MB file with heap ≤ 512 MB
- [ ] Prints total message count and per-sender counts
- [ ] Unit test parses a small synthetic XML fixture
**Verification:**
- [ ] `./gradlew :core:test` passes
- [ ] `corpus senders <export>` completes; record the run time in the PR notes
**Dependencies:** T1
**Files:** `core/.../model/RawSms.kt`, `tools/corpus/.../SmsXmlReader.kt`, `tools/corpus/.../Main.kt`, test
**Scope:** S

### Checkpoint A: Foundation
- [ ] All three modules build and the tests pass
- [ ] The corpus tool reads the real export
- [ ] T0 policy review is done, with no blocking findings (or the plan is adjusted)

---

## Phase 1: Parser on real data

### T3: Sender normalisation and bank/wallet/non-bank classification (§3, §5)
**Description:** `SenderNormalizer` strips operator prefixes and suffixes (`AD-CUBANK-S` → `CUBANK`). `SenderClassifier` groups messages by brand and decides: **bank** if messages contain masked account digits together with an amount; **wallet** if the brand is on a small built-in list of wallet names; **non-bank** if it mentions money otherwise; ignored if none of these. Add a `classify` corpus report.
**Acceptance criteria:**
- [ ] Table-driven tests cover prefix and suffix variants and the masking forms `XX1234`, `*234`, `A/c 1234`
- [ ] Money words alone ("debited") do not make a sender a bank (test covers a merchant and a telecom)
- [ ] The corpus report lists every brand with its classification and evidence counts
**Verification:**
- [ ] `./gradlew :core:test` passes
- [ ] Manual check: a human reviews the `classify` report for false banks and missed banks
**Dependencies:** T2
**Files:** `core/.../discovery/SenderNormalizer.kt`, `SenderClassifier.kt`, `WalletRegistry.kt`, tests, corpus report
**Scope:** M

### T4: Transaction extraction and skip rules (§6)
**Description:** A regex-based `TransactionParser` returns a `ParseResult`. The result is either `Transaction(amountPaise, direction, timestamp, accountRef(bank+digits | wallet), upiRef?, counterparty?)`, `Skipped(reason)` (OTP, mandate, "will be debited", failed or declined, scheduled, balance-only) or `Unreadable`. Add a `coverage` corpus report showing parsed, skipped and unreadable counts per bank sender, with sample unreadable messages printed in redacted form.
**Acceptance criteria:**
- [ ] Redacted fixtures for at least 5 banks and 2 wallets parse to the expected fields
- [ ] Every skip category has a test that is **not** counted as a transaction
- [ ] The coverage report shows the unreadable rate per sender
**Verification:**
- [ ] `./gradlew :core:test` passes
- [ ] `corpus coverage <export>`: record the overall parse rate across confirmed bank senders
**Dependencies:** T3
**Files:** `core/.../parse/TransactionParser.kt`, `ParseResult.kt`, `Patterns.kt`, `core/src/test/resources/fixtures/*`, corpus report
**Scope:** M

### T5: Templatizer (§7)
**Description:** `Templatizer.toTemplate(body)` replaces amounts → `{AMT}`, masked digits → `{DIGITS}`, dates and times, reference numbers, VPAs, phone numbers and balances with placeholders, and returns a full-string key. It keeps the token positions so the UI can later ask "tap the amount".
**Acceptance criteria:**
- [ ] The spec example becomes exactly `INR {AMT} spent from your account ending {DIGITS}`
- [ ] Two messages that differ only in variable parts give the same key; messages with different wording give different keys (full template, not a prefix)
- [ ] Number tokens are returned with their character ranges
**Verification:**
- [ ] `./gradlew :core:test` passes
- [ ] Corpus: count distinct templates among unreadable messages (expect a small number per bank)
**Dependencies:** T2 (can run in parallel with T3/T4)
**Files:** `core/.../template/Templatizer.kt`, test
**Scope:** S

### Checkpoint B: Parser coverage review
- [ ] Classification report is reviewed by a human; misclassifications are fixed or recorded
- [ ] Parse rate on confirmed bank senders is agreed acceptable (proposed target: ≥ 95% of completed-transaction messages)
- [ ] The `ParsedTransaction` contract is frozen for the app track

---

## Phase 2: Manual-mode vertical slice

### T6: Room schema v1, manual add expense, transaction list (§2)
**Description:** Create the Room database with `transactions` (source = MANUAL | SMS, amountPaise, direction, timestamp, accountId?, payee?, categoryId?, note, type), plus `accounts` and `raw_sms` (for later). Add a one-screen manual add (amount, direction, optional payee and note; big numeric keypad, no form clutter) and a transaction list.
**Acceptance criteria:**
- [ ] A user can add a debit or credit and see it in the list after an app restart
- [ ] Amounts are stored as paise (`Long`)
- [ ] The Room schema is exported to `app/schemas/` for future migrations
**Verification:**
- [ ] DAO instrumented test passes; `assembleDebug` succeeds
- [ ] Manual: add 3 entries, kill the app, reopen, all 3 are still there
**Dependencies:** T1
**Files:** `app/.../data/db/*`, `app/.../ui/add/AddExpenseScreen.kt`, `app/.../ui/list/*`, `AppContainer.kt`
**Scope:** M

### T7: Daily limit and home "₹X left today" (§12)
**Description:** Pure `core` function `dailySummary(transactions, limit, categories, today)`. It counts debits that are not self-transfer, not lent, and not in a "not daily" category; uncategorised debits count. Store the limit in DataStore. Home shows "₹X left today", or "₹X over limit" once exceeded, with zero taps from launch.
**Acceptance criteria:**
- [ ] Unit tests cover: credits excluded, uncategorised counted, over-limit shows a positive "over" amount, and the day resets at local midnight
- [ ] Home updates immediately after a manual add
- [ ] First launch asks for the limit once
**Verification:**
- [ ] `:core:test` and `:app:testDebugUnitTest` pass
- [ ] Manual: set ₹500, add ₹600 debit → "₹100 over limit"
**Dependencies:** T6
**Files:** `core/.../limit/DailySummary.kt`, `app/.../ui/home/*`, `app/.../data/prefs/*`, tests
**Scope:** M

### T8: Categories and the "not daily" flag (§11)
**Description:** Add a `categories` table with seeded defaults (Food, Travel, Shopping, Bills*, Rent*, EMI*; * = not daily). The user can add a category or toggle "not daily". Tap a transaction to see one-tap category buttons. The daily summary respects the flag.
**Acceptance criteria:**
- [ ] Tagging a debit as Rent removes it from today's spend instantly
- [ ] Users can create a category and toggle its "not daily" flag
- [ ] Room migration v1→v2 is tested
**Verification:**
- [ ] Migration test passes; unit tests pass
- [ ] Manual: ₹600 untagged → over limit; tag as Rent → back under the limit
**Dependencies:** T7
**Files:** `app/.../data/db/CategoryDao.kt`, migration, `app/.../ui/category/*`, tests
**Scope:** M

### Checkpoint C: Usable manual-mode app
- [ ] Manual mode works end to end on a device: set limit, add, tag, see "left today"
- [ ] All tests pass
- [ ] Human decides on layout (Open Question 1) before more UI is built

---

## Phase 3: Automatic mode

### T9: Permission onboarding and mode switching (§1, §2)
**Description:** Add the onboarding screen with Play-compliant prominent disclosure, then request `READ_SMS` and `RECEIVE_SMS`. If granted → Automatic; if denied → Manual with no nagging. Settings has a mode toggle that re-requests the permission as needed. Handle the permission being revoked while the app is running.
**Acceptance criteria:**
- [ ] Deny → app fully usable in Manual mode
- [ ] Grant later from settings → switches to Automatic
- [ ] Revoking in system settings → falls back to Manual on the next resume
**Verification:**
- [ ] Manual on an API 33+ emulator: grant, deny and revoke paths
**Dependencies:** T8
**Files:** `AndroidManifest.xml`, `app/.../ui/onboarding/*`, `app/.../ui/settings/*`, `ModeRepository.kt`
**Scope:** M

### T10: Inbox import, account discovery, confirmation screen (§3, §4)
**Description:** A WorkManager job reads the inbox through `ContentResolver` in batches, saves the messages to `raw_sms`, and runs `SenderClassifier` and account extraction. Show progress. When it finishes, the confirmation screen lists `BANK | digits` accounts and wallets, each with two big buttons: **Mine** / **Not mine, ignore**. Answers are saved and can be changed in settings.
**Acceptance criteria:**
- [ ] Import of 50k+ synthetic messages completes without ANR, with visible progress
- [ ] "Not mine" accounts never appear again and are never counted
- [ ] Answers can be changed in settings
**Verification:**
- [ ] Instrumented test with a seeded fake SMS provider
- [ ] Manual: on a real phone, the discovered accounts match the user's actual accounts
**Dependencies:** T9, Checkpoint B
**Files:** `app/.../sms/InboxReader.kt`, `app/.../work/ImportWorker.kt`, `app/.../ui/confirm/*`, `AccountDao.kt`, migration
**Scope:** M

### T11: Ledger ingest from parsed SMS (§4 digit matching, §6)
**Description:** Run messages from confirmed senders through templates first, then `TransactionParser`, and create SMS transactions linked to their `raw_sms` row. Account matching: 4 digits match directly; 3 digits auto-match only when exactly one account at that bank fits, otherwise the message goes to Needs-you (stubbed as a flag until T13). Ingest is idempotent: the same SMS never creates two rows.
**Acceptance criteria:**
- [ ] 3-digit ambiguity cases are tested (0, 1 and 2+ candidates)
- [ ] Re-running ingest produces no duplicates
- [ ] Home total includes SMS debits alongside manual ones
**Verification:**
- [ ] `:core:test` (matching logic) and instrumented ingest test pass
- [ ] Manual: after import, today's total matches the bank SMS for today
**Dependencies:** T10
**Files:** `core/.../account/AccountMatcher.kt`, `app/.../ledger/IngestService.kt`, tests
**Scope:** M

### T12: Live capture through the RECEIVE_SMS receiver
**Description:** A `BroadcastReceiver` for incoming SMS hands off to a short WorkManager job that runs the same ingest. On every app open, a catch-up scan processes inbox messages newer than the last processed id, in case the OEM killed the receiver.
**Acceptance criteria:**
- [ ] A new bank SMS updates the home total within seconds while the app is in the background
- [ ] A message missed by the receiver is picked up on the next app open
- [ ] A message is never processed twice by the receiver and the catch-up scan
**Verification:**
- [ ] Manual: `adb emu sms send` on the emulator; check the home screen
**Dependencies:** T11
**Files:** `app/.../sms/SmsReceiver.kt`, `app/.../work/IngestWorker.kt`, manifest
**Scope:** S

### T13: "Needs you" queue and "Couldn't read" list (§7, §15)
**Description:** A `needs_you` table holds typed items (UNREADABLE, AMBIGUOUS_ACCOUNT, POSSIBLE_TRANSFER, NEW_PAYEE). A single queue screen shows one card per item with big one-tap answers, and a badge on home shows the count. Any message from a confirmed sender that the parser cannot read lands here as UNREADABLE.
**Acceptance criteria:**
- [ ] No confirmed-sender message is ever dropped: every message is a transaction, an explicit skip, or a queue item (asserted by an ingest test)
- [ ] An ambiguous 3-digit match is resolved with one tap per account choice
- [ ] Badge count updates live
**Verification:**
- [ ] Unit and instrumented tests pass
- [ ] Manual: send an oddly worded bank SMS → it appears under "Couldn't read"
**Dependencies:** T11
**Files:** `app/.../data/db/NeedsYouDao.kt`, `app/.../ui/needsyou/*`, migration
**Scope:** M

### T14: Template learning, plus view and undo templates (§7)
**Description:** From an UNREADABLE card, ask: is it a transaction? → Debit / Credit → tap the number that is the amount (numbers highlighted from T5 token ranges). Save template key → answer. Re-run pending unreadables so matching ones resolve at once. A settings screen lists learned templates; undo deletes one and re-derives the affected transactions.
**Acceptance criteria:**
- [ ] After teaching one message, a second message with the same template is parsed automatically
- [ ] Answering "Not a transaction" also learns (future matches are skipped)
- [ ] Undoing a template removes the transactions it produced and returns those messages to "Couldn't read"
**Verification:**
- [ ] `:core:test` for template apply; instrumented test for learn → undo
- [ ] Manual: teach one, send another like it, see it auto-added
**Dependencies:** T5, T13
**Files:** `core/.../template/TemplateApplier.kt`, `app/.../ui/needsyou/TeachCard.kt`, `app/.../ui/settings/TemplatesScreen.kt`, `TemplateDao.kt`
**Scope:** M

### Checkpoint D: Auto-capture end to end
- [ ] On a real phone: install → grant → import → confirm accounts → today's total is correct
- [ ] Live SMS and the catch-up scan both work
- [ ] Every unread message is visible; template learning works
- [ ] Human review before the correctness work

---

## Phase 4: Ledger correctness

### T15: Label-only matching (§8)
**Description:** SMS from non-bank senders (merchants, payment apps) never create amounts. `LabelMatcher` attaches them to a transaction: first by matching UPI reference; otherwise same amount, same direction and within ±30 min. Two bank messages are never merged.
**Acceptance criteria:**
- [ ] Table tests: ref match, no-ref window match, outside window → no match, two ₹20 bank debits a minute apart stay as two
- [ ] A matched label sets the payee display; unmatched labels are stored but never counted
**Verification:**
- [ ] `:core:test` passes; corpus `dedup` report shows the count of labels attached
**Dependencies:** T11
**Files:** `core/.../ledger/LabelMatcher.kt`, ingest hook, tests
**Scope:** S

### T16: Wallets (§5)
**Description:** Wallet accounts are keyed by sender name. The parser tells apart "paid from wallet balance" (counts as a debit) and "paid via wallet UPI from bank" (label-only; goes through `LabelMatcher`). A bank → wallet top-up is flagged for self-transfer pairing (T17).
**Acceptance criteria:**
- [ ] Wallet-balance payment counts once
- [ ] Wallet-UPI payment plus its bank debit counts once
- [ ] Wallet fixtures for at least 2 wallets pass
**Verification:**
- [ ] `:core:test` passes
**Dependencies:** T15
**Files:** `core/.../parse/WalletPatterns.kt`, `core/.../ledger/*`, fixtures
**Scope:** S

### T17: Self-transfer pairing (§9)
**Description:** `TransferPairer` pairs a debit from one owned account or wallet with a credit to another owned one: same amount within fee tolerance, close in time. Confident pairs are linked automatically; borderline ones go to Needs-you as POSSIBLE_TRANSFER. Both rows stay in the ledger, linked and marked as transfer, and are excluded from spend and income. Pairs can be undone.
**Acceptance criteria:**
- [ ] ₹1,000 out and ₹995 in → paired; ₹1,000 out and ₹700 in → not paired
- [ ] Bank → wallet top-up is paired
- [ ] Paired rows are excluded from the daily summary and from income
**Verification:**
- [ ] `:core:test` passes; manual check of the transfer badge in the list
**Dependencies:** T16, T13
**Files:** `core/.../ledger/TransferPairer.kt`, `pairings` table + migration, tests
**Scope:** M

### T18: Manual + bank SMS auto-merge (§8 default)
**Description:** When a bank SMS matches a manual entry (same amount and direction, within the merge window), merge them. The SMS supplies account and timestamp; the manual entry keeps its category and note. Store this as a merge decision that can be undone.
**Acceptance criteria:**
- [ ] Add manual ₹250, then receive a matching SMS → one row, with the category kept
- [ ] Undo splits it back into two rows
- [ ] Two SMS are never merged with each other by this rule
**Verification:**
- [ ] `:core:test` and instrumented test pass
**Dependencies:** T17
**Files:** `core/.../ledger/ManualMerger.kt`, `merges` table, tests
**Scope:** S

### T19: Lent (§9)
**Description:** Add a one-tap "Lent" action on a debit (never inferred). Lent rows are shown grey and excluded from spend. An "Owed to you" view lists amounts per counterparty (VPA or phone). A credit from the same counterparty closes the entry or reduces the outstanding amount instead of counting as income; any excess counts as income. Users can write off an entry.
**Acceptance criteria:**
- [ ] Lend ₹1,000 → repaid ₹400 → ₹600 outstanding, and the ₹400 is not counted as income
- [ ] Write-off closes the entry and removes it from reminders
- [ ] Lent rows render grey and are excluded from the daily limit
**Verification:**
- [ ] `:core:test` passes; manual check of the grey styling and the owed view
**Dependencies:** T15 (can run in parallel with T16–T18)
**Files:** `core/.../ledger/LentLedger.kt`, `app/.../ui/lent/*`, `lent` table + migration
**Scope:** M

### Checkpoint E: Totals are correct
- [ ] Corpus `month-summary` report for one sample month is checked against the bank statement by hand
- [ ] No double counting found across labels, wallets, transfers and manual merges
- [ ] Human review

---

## Phase 5: Edit, categorise, history

### T20: Tap-row edit with undo (§10)
**Description:** Tapping any row opens an edit sheet for amount, direction, type, account, category, payee, and "Not a transaction". Each edit is saved as an override decision. A snackbar offers Undo, and totals recompute at once.
**Acceptance criteria:**
- [ ] Every listed field is editable from a single tap on the row
- [ ] Undo restores the previous state exactly
- [ ] "Not a transaction" removes the row from totals but keeps it reachable in history
**Verification:**
- [ ] Instrumented UI test: edit → undo
**Dependencies:** Checkpoint E
**Files:** `app/.../ui/edit/*`, `overrides` table, `core/.../ledger/Derive.kt`
**Scope:** M

### T21: "Just this one / All like this", sticky payee tags, bulk actions (§10, §11, §15)
**Description:** Tagging a payee (name, VPA or phone) stores a payee → category rule that pre-tags future payments. Edits that could apply more widely ask once: Just this one / All like this (same template or payee). Needs-you shows bulk cards such as "Tag all 6 Swiggy payments".
**Acceptance criteria:**
- [ ] Tagging MYNTRA once pre-tags the next MYNTRA payment
- [ ] "All like this" applies to existing matching rows, and can be undone as one action
- [ ] A bulk card resolves N items with one tap
**Verification:**
- [ ] Unit and instrumented tests pass
**Dependencies:** T20
**Files:** `core/.../rules/PayeeRules.kt`, `app/.../ui/edit/ScopePrompt.kt`, `app/.../ui/needsyou/BulkCard.kt`
**Scope:** M

### T22: History search, filters, month total (§12, §14)
**Description:** The history screen has search by payee or amount and filters for date range, account, category and type. A month header shows total spent, with no target. Tapping a row opens the T20 edit sheet.
**Acceptance criteria:**
- [ ] Search "250" finds ₹250 rows; search "swiggy" finds Swiggy rows
- [ ] Filters combine correctly (account + category + date range)
- [ ] Month total equals the sum of counted debits for that month
**Verification:**
- [ ] DAO query tests; manual check against 10k seeded rows for smooth scrolling
**Dependencies:** T20
**Files:** `app/.../ui/history/*`, `TransactionDao.kt` queries
**Scope:** M

### Checkpoint F: Feature complete minus notifications
- [ ] All spec §2–§15 behaviours can be shown on a device
- [ ] Layout (§16) design applied
- [ ] Human review

---

## Phase 6: Notifications

### T23: Daily-limit alerts and battery-optimisation prompt (§13)
**Description:** After each ingest, check thresholds and post notifications at 80% and 100%, each at most once per local day (state kept in DataStore). Request `POST_NOTIFICATIONS` on Android 13+; if denied, the on-screen limit still works. A one-time prompt asks the user to exempt the app from battery optimisation.
**Acceptance criteria:**
- [ ] Crossing 80% and then 100% sends exactly two notifications that day, and none for later transactions
- [ ] Denied permission → no crash, and home still shows the limit
- [ ] Alert state resets the next day
**Verification:**
- [ ] Unit test of threshold and once-per-day logic; manual check with emulator SMS
**Dependencies:** T12
**Files:** `core/.../limit/AlertPolicy.kt`, `app/.../notify/*`
**Scope:** S

### T24: Lent reminder on the 1st (§13)
**Description:** A periodic WorkManager job runs daily; on the 1st, if any lent entry is outstanding, it posts "₹X owed to you by N people" once.
**Acceptance criteria:**
- [ ] Fires on the 1st only when the outstanding amount is greater than 0; written-off entries are excluded
- [ ] Never sent twice in the same month
**Verification:**
- [ ] Unit test with an injected clock
**Dependencies:** T19, T23
**Files:** `app/.../work/LentReminderWorker.kt`
**Scope:** XS

---

## Phase 7: Release

### T25: Privacy policy, Data Safety, permissions declaration, demo video
**Description:** Finalise the T0 drafts against the built app. Host the privacy policy, fill in Data Safety (no data collected or shared off-device), complete the SMS Permissions Declaration, and record the demo video of the core SMS flow. Set `allowBackup=false` or encrypted backup rules.
**Acceptance criteria:**
- [ ] Privacy policy URL is live and accurate
- [ ] Declaration and Data Safety answers match the actual app behaviour
**Verification:**
- [ ] Manual: human signs off on every declaration
**Dependencies:** Checkpoint F, T24
**Files:** `docs/*`, manifest
**Scope:** S

### T26: Release build and internal testing track
**Description:** Set up a signed release build with R8, check that no SMS content is logged in the release build, upload to the internal testing track, and fix pre-launch report findings.
**Acceptance criteria:**
- [ ] Signed AAB uploaded; pre-launch report has no crashes
- [ ] Release APK has no INTERNET permission (proves nothing leaves the device)
**Verification:**
- [ ] `./gradlew bundleRelease`; `aapt dump permissions` shows no INTERNET
**Dependencies:** T25
**Files:** `app/build.gradle.kts`, `proguard-rules.pro`
**Scope:** S

### Checkpoint G: Ready for Play review
- [ ] All tasks checked, all tests pass
- [ ] Human submits for production review
