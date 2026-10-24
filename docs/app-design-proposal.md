# SMS Ledger v0: Design & Decisions Proposal

**Status:** proposal for review · **Date:** 23 Oct 2026 · **Source spec:** `sms-ledger-v0-features.md` (sections written §N)

This document gathers every product, UX, visual and technical decision for v0 in one place. It also lists what still needs your sign-off. The detailed docs it summarises are:

| Doc | What it covers |
|---|---|
| [`tasks/plan.md`](../tasks/plan.md) · [`tasks/todo.md`](../tasks/todo.md) | Delivery plan, 27 tasks with acceptance criteria |
| [`docs/flows.md`](flows.md) | Every screen and flow, including states |
| [`docs/design-system.md`](design-system.md) | Visual language |

Every decision is labelled with one of three statuses:

- **Decided:** from the spec, already agreed.
- **Proposed:** my recommendation, built into the preview. Please confirm.
- **Open:** needs your input before the related work starts.

---

## 1. Summary

SMS Ledger is a native Android app (Kotlin, Jetpack Compose, Room). It reads bank and wallet SMS on the phone, builds a spending ledger without double counting, and answers one question with zero taps: **"₹X left today."** Nothing leaves the device. If SMS permission is denied, the app works in Manual mode with the same features, minus automatic capture.

The two biggest risks are the **Play Store SMS permission review** and **parser accuracy on real Indian bank SMS**. The plan tackles both first, then builds the app in thin end-to-end slices.

## 2. Where things stand

| Item | State |
|---|---|
| Spec | Done (`sms-ledger-v0-features.md`) |
| Plan + tasks | Done: 27 tasks, 7 checkpoints |
| Flows | Done: all screens with loading, error and empty states |
| Design system | Done, implemented in code |
| T1: project skeleton | ✅ Done: builds, launches, tests pass |
| UI preview | ✅ All flows built in Compose on sample data, checked on the emulator in light and dark |
| Core logic so far | ✅ `formatRupees`, `formatRupeesWhole`, `limitStatus`, `AmountInput` (24 tests passing) |
| Real data, SMS reading, parser | Not started (T2 onwards) |

---

## 3. Product decisions

### 3.1 Platform and privacy (§1): Decided

- Native Android, Kotlin, Room/SQLite. **No backend, no sync, no analytics, no crash SDKs.**
- Distributed on the Play Store under the **"SMS-based money management"** permission exception. Requires the Permissions Declaration Form, a privacy policy and an accurate Data Safety form.
- **Proposed:** the release build ships **without the INTERNET permission**. This proves to users and reviewers that data cannot leave the phone. Android backup is turned off (`allowBackup=false`).

### 3.2 Modes (§2): Decided

| Mode | When | Behaviour |
|---|---|---|
| Automatic | SMS permission granted | SMS capture plus everything else |
| Manual | Permission denied or unavailable | Everything except capture. Manual entries count everywhere. |

The user can switch modes at any time. If the permission is revoked outside the app, the app falls back to Manual on next open, with a banner (Proposed).

### 3.3 Finding banks, accounts and wallets (§3–§5): Decided

- **No hardcoded bank list.** Senders are grouped by brand after stripping operator prefixes and suffixes (`AD-CUBANK-S` → `CUBANK`).
- A sender counts as a **bank** only when its messages contain **masked account digits together with an amount**. Money words alone are not enough.
- **Wallets** are recognised by sender name, and each wallet is its own account.
- Each account is identified as **bank + masked digits** (`CUB | 1234`). The user answers **Mine / Not mine** once; "Not mine" is permanent.
- Digit matching: 4 digits match directly. 3 digits match only when exactly one known account fits; otherwise the message goes to Needs you.

### 3.4 Parsing and failures (§6–§7): Decided

- Regex is the first-line extractor. It pulls out amount, direction, timestamp, account, UPI reference and counterparty.
- Skipped, never counted: OTPs, mandates, "will be debited", failed or declined, scheduled, and balance-only messages.
- **Nothing is dropped silently.** Any unreadable message from a confirmed sender goes to **"Couldn't read"**.
- **Template learning:** the variable parts of a message are replaced with placeholders (`{AMT}`, `{DIGITS}`, …). The user teaches it once, and every later message with the same template is handled automatically. The key is the **full** template. Every learned template can be viewed and undone.

### 3.5 Double counting (§8–§9): Decided

1. Only **bank** and **wallet-balance** SMS add money to the ledger. Merchant and payment-app SMS only attach labels.
2. Label matching: **UPI reference first**. Without one, match on **same amount + same direction + within ±30 min**.
3. **Two bank messages are never merged.** Two ₹20 debits a minute apart are two payments.
4. A **wallet paid through its own UPI from a bank** is label-only. A **wallet paid from its own balance** counts.
5. **Self-transfers** (including bank → wallet top-ups) are **paired, not merged**: both rows stay, linked, and count toward neither spend nor income. A small fee difference is allowed.
6. **Manual entry + matching bank SMS** merge automatically. The SMS supplies the account and time; the manual entry keeps its category and note. The merge can be undone. (§8 default)

### 3.6 Transaction types (§9): Decided

| Type | Daily limit | Income | Display |
|---|---|---|---|
| Debit | Counts, unless its category is "not daily" | — | normal |
| Credit | — | Counts | `+₹` in green |
| Self-transfer | No | No | ⇄ badge, "Transfer" |
| Lent | No | No | **grey plus a "Lent" label** |

**Lent** is set by the user only, never inferred. It is tracked per person (by VPA or phone). A repayment from the same person closes the entry instead of counting as income, partial repayments reduce what's owed, and an entry can be written off.

### 3.7 Editing, categories, limit, history (§10–§14): Decided

- **Every automatic decision can be edited by tapping the row.** Where a change could apply more widely, the app asks once: **"Just this one / All like this."** Every change can be undone, and totals recompute immediately.
- **No automatic categorisation.** A category set for a payee sticks for that payee's future payments. A category can be marked **"not daily"** (Rent, EMI, Bills). There are no per-category or monthly limits.
- **Daily limit:** shows "₹X left today", and **"₹X over limit"** once exceeded, never a negative number. Uncategorised debits **count** (overcounting is safer).
- **Month view:** total spent only, with no target.
- **Notifications:** one alert at 80% and one at 100%, each at most once per day. One lent reminder on the 1st of the month, only if something is outstanding.
- **History:** search by payee or amount; filter by date, account, category and type.

### 3.8 Out of scope for v0: Decided

ML classification, automatic categorisation, per-category or monthly limits, cloud sync or multi-device, any server, spreadsheet import or export.

---

## 4. Defaults I've proposed (please confirm)

| # | Decision | Proposal | Why |
|---|---|---|---|
| D1 | Minimum Android | **8.0 (API 26)** | Covers ~97% of active devices; `java.time` is available |
| D2 | Target / compile SDK | **target 36, compile 37** | Current Compose needs compileSdk 37; targetSdk stays one step behind |
| D3 | Label-match window | **±30 min** | From the spec |
| D4 | Manual + SMS merge window | **6 hours** | The spec says "a few hours" |
| D5 | Self-transfer window | **2 hours** | Bank credit SMS can be delayed |
| D6 | Self-transfer fee tolerance | **≤ ₹50 or 2%, whichever is smaller** | Covers wallet top-up fees without pairing unrelated payments |
| D7 | Day boundary | **Local midnight** | Matches how people think about "today" |
| D8 | Currency | **INR only.** Other currencies go to "Couldn't read". | v0 scope |
| D9 | Money storage | **`Long` paise**, never floating point | Exact arithmetic |
| D10 | Totals display | **Rounded to whole rupees.** Single transactions stay exact. | "₹24,997 spent" reads better than "₹24,996.50" |
| D11 | Placeholder package id | `com.smsledger` | Change before the first Play upload |

---

## 5. UX design

### 5.1 Principles

1. **The Today screen answers the question with zero taps.** The app opens on Today, and the remaining amount is the largest thing on screen.
2. **Show value before asking.** Onboarding shows a sample "₹340 left today" and sets the limit *before* asking for SMS.
3. **Ask for each permission when it's needed:** SMS during onboarding (it's the core feature), notifications when an alert would actually help, battery optimisation only after alerts are on.
4. **One "Needs you" queue** for everything uncertain, answered with one tap each.
5. **No forms.** Big buttons, a custom keypad, and chips instead of dropdowns where possible.
6. **Every change can be undone,** through a snackbar plus a permanent route in Settings.

### 5.2 App map

```
First launch:  Welcome → Daily limit → SMS disclosure ─┬─ Allow → Import → "Which are yours?" → Today
                                                       └─ Manual → "You'll add expenses yourself" → Today

Tabs:   TODAY ──── Needs you (banner + badge) ── Teach a message
          │   ├── Add expense (+ button, sheet)
          │   ├── Owed to you (card, only if > ₹0)
          │   └── Row tap → Edit sheet
        HISTORY ── search · Account / Category / Type filters · row → Edit sheet
        SETTINGS ─ limit · mode · accounts · categories · learned messages · payee rules
                   alerts · battery · privacy policy · delete all data
```

### 5.3 Key screens

| Screen | Job | Main action | Notes |
|---|---|---|---|
| **Today** | Answer "how much is left?" | Glance; + to add | Hero number, day meter, Needs you banner, contextual cards one at a time, today's rows, month total |
| **Needs you** | Clear everything uncertain | One tap per card | Six card types: Couldn't read, Which account?, Possible self-transfer, New payee, Tag in one go, New account found |
| **Teach a message** | Learn a new SMS format | Out/In → tap the amount | Numbers in the message are highlighted; date fragments are skipped |
| **Add expense** | Manual entry | Keypad → Save | Asks before discarding a typed amount |
| **Edit sheet** | Fix any decision | Lent / Self-transfer / Not a payment / Category | Scope prompt only when it matters |
| **History** | Look back | Search, filter | Day headers stay at the top while scrolling and show day totals |
| **Owed to you** | Track lending | Mark repaid / Write off | Write-off asks for confirmation and can be undone |

All loading, error and empty states are listed per screen in [`flows.md`](flows.md).

### 5.4 UX decisions to confirm (flows.md §14)

| # | Decision | Proposal (built) | Alternative |
|---|---|---|---|
| U1 | Where Needs you lives | **Banner on Today plus a badge**, not a tab | A 4th tab: easier to find, but empty most of the time |
| U2 | Order of onboarding | **Limit before the SMS ask** | SMS first: one fewer screen before the ask, but value shown later |
| U3 | When to ask for notifications | **First day spending passes 50%** | During onboarding: higher opt-in, less context |
| U4 | Account confirmation | **Can leave early;** the rest go to Needs you | Require every card answered |
| U5 | Default daily limit | **₹500 suggested,** with ₹300 / ₹1,000 / ₹2,000 chips | Start empty |

---

## 6. Visual design: "paper ledger"

A calm, trustworthy money tool, not a fintech ad.

| Token | Light | Dark | Use |
|---|---|---|---|
| Background | `#F7F5F0` warm paper | `#131412` warm charcoal | Every screen |
| Ink | `#1C1B18` | `#E6E3DC` | Text |
| Primary: ledger green | `#1E5B4A` | `#8ED1B5` | Primary actions and income **only** |
| Warning: amber | `#B86E00` | `#F0B45A` | 80% of limit |
| Over: brick | `#B3392B` | `#F1A497` | Over limit |
| Attention: ochre | `#8A5A12` | `#F0C27A` | Needs you count and labels |

- **Type:** system sans. Money uses **tabular figures** so digits don't jump. The hero is 72sp, with a smaller, quieter ₹ symbol.
- **Shape:** corner radii follow a hierarchy (8 chips · 12 buttons · 16 cards · 28 sheets). **No shadows, no gradients.** Depth comes from tonal surfaces.
- **Spacing:** 4-pt scale, 20dp screen gutter.
- **Signature component:** the **day meter**, a thick track with a notch at 80%, filled green, then amber, then red, with "₹409 spent · 81%" written below it.
- **Accessibility:** colour is never the only signal (lent rows are grey *and* labelled; status always includes words). Tap targets are 48dp or more. The hero is read aloud by TalkBack as one phrase. Dark mode is designed alongside light.
- **Rejected:** purple palettes, hero gradients, card grids, pie charts, negative balances, and confirmation dialogs for known cases.

---

## 7. Technical architecture

### 7.1 Modules: Proposed (built)

| Module | Contents | Why |
|---|---|---|
| `core/` | Pure Kotlin/JVM: discovery, parsing, templates, matching, pairing, lent maths, limit logic, formatting | Holds most of the risk; tests run in seconds on the JVM with no device |
| `tools/corpus/` | Developer command-line tool that streams the ~400 MB SMS export through `core` and prints reports | Measures parser accuracy on real data; never shipped |
| `app/` | Room, SMS reader and receiver, WorkManager, notifications, Compose UI | Android-only code, no business rules |

### 7.2 Data model principle: Proposed

**Raw SMS are stored unchanged. The ledger is derived from them plus user decisions.**

```
raw_sms (confirmed senders only) ─┐
learned templates ────────────────┤
account answers (Mine/Not mine) ──┤
payee → category rules ───────────┼──► derive ──► transactions shown ──► daily summary / history
overrides (per-row edits) ────────┤
merges · pairings · lent marks ───┤
manual entries ───────────────────┘
```

- **Undo** means deleting a decision row and recomputing. "All like this" is a single rule row.
- When the parser improves, past messages are re-parsed for free.
- Proposed tables: `raw_sms`, `senders`, `accounts`, `templates`, `payee_rules`, `categories`, `manual_entries`, `overrides`, `merges`, `pairings`, `lent_entries`, `needs_you`, plus a derived `transactions` table that is rebuilt incrementally.

### 7.3 Processing pipeline

```
SMS ──► normalise sender ──► confirmed bank/wallet? ──no──► (label candidate or ignore)
                                   │ yes
                                   ▼
             learned template? ──yes──► apply
                   │ no
                   ▼
             regex parser ──► Transaction | Skipped(reason) | Unreadable ──► "Couldn't read"
                   │
                   ▼
   account match (4-digit / unique 3-digit, else Needs you)
                   ▼
   label matching (UPI ref → amount + direction + ±30 min)
                   ▼
   wallet rules (balance vs UPI) ─► self-transfer pairing ─► manual merge ─► lent closure
                   ▼
   derived ledger ─► limitStatus ─► Today · alerts
```

Each step is a pure function in `core` with table-driven tests.

### 7.4 Background work: Proposed

- **First import:** a WorkManager job reads the inbox in batches through the content provider, with a progress notification, so the UI never blocks.
- **Live capture:** an `SMS_RECEIVED` receiver hands each message to a short job. **Each time the app opens,** a catch-up scan picks up anything a battery-killing phone maker caused it to miss. Ingest is idempotent, so the same message never creates two rows.
- **Alerts:** checked after each ingest; the once-per-day state is kept in DataStore. The lent reminder runs as a daily periodic job that only fires on the 1st.

### 7.5 Privacy and security: Proposed

- **Only messages from confirmed senders are stored.** Everything else is classified in memory and discarded. This is what makes the disclosure-screen promise true.
- No INTERNET permission, no analytics or crash SDKs, `allowBackup=false`, and message bodies are never logged.
- The test corpus is **never committed**; unit fixtures are hand-redacted.

### 7.6 Toolchain: Decided (in use)

| Component | Version |
|---|---|
| Gradle | 9.8.2 (runs on Android Studio's bundled JDK 25) |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin) |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 (Material 3 1.4) |
| Tests | JUnit 4 (JVM); Compose UI tests from T6 |
| DI | Manual (`AppContainer`); Hilt deferred |
| State in preview | `DemoStore` (sample data), replaced by Room repositories from T6 |

---

## 8. Delivery plan

| Phase | Tasks | Goal | Checkpoint |
|---|---|---|---|
| 0: Foundation and policy | T0 policy pre-check · ✅T1 skeleton · T2 corpus harness | Can read the real export; no blocking Play policy issues | A |
| 1: Parser on real data | T3 sender discovery · T4 parsing + skip rules · T5 templatizer | **≥ 95%** of completed-transaction messages parsed (proposed target) | B: review with a human |
| 2: Manual mode slice | T6 Room + add · T7 limit + Today · T8 categories | Usable app without SMS | C |
| 3: Automatic mode | T9 permissions · T10 import + confirm · T11 ingest · T12 live capture · T13 Needs you · T14 teach | End to end on a real phone | D |
| 4: Correctness | T15 labels · T16 wallets · T17 transfers · T18 manual merge · T19 lent | No double counting; a sample month reconciles against the bank statement | E |
| 5: Edit and history | T20 edit + undo · T21 All like this + bulk · T22 history | All of §2–§15 can be demonstrated | F |
| 6: Notifications | T23 limit alerts · T24 lent reminder | — | — |
| 7: Release | T25 privacy, Data Safety, declaration · T26 release build | Submitted to Play internal testing | G |

The UI preview means Phases 2–5 mainly connect real data to screens that already exist. Each task follows the same loop: failing test, minimal code, full suite, build, commit.

---

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Play rejects the SMS permission | **High** | T0 early; a clear disclosure (already designed); SMS is plainly the core feature; Manual mode can ship while we appeal |
| Parser misses real bank formats | High | Measure on the 400 MB corpus before building on it; "Couldn't read" plus template learning keep misses visible |
| Double counting | High | Each rule is a pure function with table-driven tests; reconcile a real month at Checkpoint E |
| Battery-saving phone makers delay capture and alerts | Medium | Catch-up scan on open; battery-optimisation prompt once alerts are on |
| Large-inbox import is slow | Medium | Batched cursor in WorkManager with visible progress |
| Message bodies leak through logs or backups | High | No network, no backups, never log bodies, store only confirmed senders |

---

## 10. Decisions needed from you

| # | Question | My recommendation |
|---|---|---|
| Q1 | Confirm the UX decisions **U1–U5** (§5.4) | As built |
| Q2 | Confirm the defaults **D1–D11** (§4) | As listed |
| Q3 | Where is the ~400 MB SMS export, and is it in "SMS Backup & Restore" XML format? Whose messages are they? | Needed for T2; decides how strictly fixtures are redacted |
| Q4 | App name and package id for the Play listing | Needed before T26 |
| Q5 | Is a Play Console developer account set up? | Needed for T25–T26 |
| Q6 | Parser accuracy bar for Checkpoint B | ≥ 95% of completed-transaction SMS from confirmed senders |
| Q7 | Visual direction ("paper ledger", green/amber/brick) | Keep. A brand pass (logo, launcher icon) can come before release. |
| Q8 | Next step | **T2 + T0 in parallel** (export reader + policy check), then T3–T5. Alternatively, start T6 to connect real data to the existing UI first. |

Once you've answered Q1–Q3, I can continue with the next tasks in order.
