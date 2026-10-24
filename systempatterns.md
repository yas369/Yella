# System Patterns: SMS Ledger

**How** the app is built: architecture, the patterns it follows, how data moves, and the conventions to keep. For *why* and *what*, see [`productcontext.md`](productcontext.md).

Status labels: **Built** = in the code today · **Planned** = designed, arrives with the plan task named.

---

## 1. Architecture at a glance

```
┌──────────────────────────────── app/ (Android) ────────────────────────────────┐
│  MainActivity ─ splash, edge-to-edge, theme, shortcut intents                    │
│     │                                                                           │
│  SmsLedgerApp (shell) ─ nav bar, FAB, overlays, snackbar, sheets                 │
│     │                                                                           │
│  Routes ── bind state + callbacks ──► Screens (stateless Compose)                │
│     │                                                                           │
│  DemoStore (Built, preview) ──► Room repositories (Planned, T6+)                │
│     │            ▲                                                              │
│     │            └── SMS reader / receiver, WorkManager (Planned, T9–T12)       │
│  widget/ ─ Glance widget reads a published snapshot                             │
└─────┼───────────────────────────────────────────────────────────────────────────┘
      │ calls
┌─────▼──────────── core/ (pure Kotlin, JVM) ─────────────┐   ┌── tools/corpus/ ──┐
│  money/   formatRupees, formatRupeesWhole, AmountInput  │◄──│ CLI over the SMS  │
│  limit/   limitStatus                                   │   │ XML export        │
│  ledger/  LedgerMath, CategoryRules, LentBook           │   │ (Planned, T2–T5)  │
│  parse/ discovery/ template/  (Planned, T3–T5)          │   └───────────────────┘
└─────────────────────────────────────────────────────────┘
```

**The one rule:** *business rules live in `core`, Android lives in `app`.* `core` has no Android imports, so every rule is unit-tested on the JVM in seconds, and the preview store and the real database app use the same code.

| Module | Holds | Never holds |
|---|---|---|
| `core/` | Money maths, limit status, ledger rules; later sender discovery, parsing, templates, matching | Android, Compose, I/O |
| `app/` | UI, theme, widget, store and repositories, SMS access, background work | Ledger rules (it calls `core`) |
| `tools/corpus/` | Developer command-line tool that runs `core` over the real export | Anything shipped in the app |

---

## 2. Domain model

### Money
- **Always `Long` paise.** No `Double` anywhere: ₹250.50 is `25_050`.
- **Display:** `formatRupees` (exact, Indian grouping: ₹1,23,456, decimals only when non-zero) for single rows; `formatRupeesWhole` (rounded) for totals and headlines.
- **Input:** `AmountInput` holds the keypad editing rules (one decimal point, at most 2 decimals, at most 7 rupee digits).

### Ledger entry (`core/ledger/Ledger.kt`), Built
```kotlin
LedgerEntry(id, payee, amountPaise, kind: Debit | Credit | Transfer | Lent, date, categoryId?, excluded)
```
UI and storage models **map into** this; the rules only ever see `LedgerEntry`.

### Transaction types (spec §9)

| Kind | Daily limit | Month total | Income |
|---|---|---|---|
| Debit | Yes, unless its category is "not daily" | Yes | — |
| Credit | — | — | Yes (unless it repays a debt) |
| Transfer | No | No | No |
| Lent | No | No | No |
| `excluded` ("not a transaction") | No | No | No |

---

## 3. Core rule patterns, Built

All are **pure functions or immutable values**: data in, data out, no side effects.

| Rule | File | How it works |
|---|---|---|
| Daily spend | `LedgerMath.spentOn` | Today's debits, not excluded, category not in the not-daily set. Uncategorised counts. |
| Month total | `LedgerMath.spentInMonth` | Debits in that calendar month, every category, not excluded |
| Limit status | `limitStatus` | Returns `remaining`, `over`, `fraction` and `level` (Ok / Warning at 80% / Over only when exceeded). Never negative. |
| Category scope | `CategoryRules.apply` | "Just this one" changes the target row; "All like this" changes every **debit** from the same payee |
| Payee identity | `CategoryRules.payeeKey` | `trim().lowercase()`, so "MYNTRA" and "Myntra " are the same payee |
| Sticky tags | `CategoryRules.categoryFor` | Payee key → category rule pre-tags new debits |
| Owed to you | `LentBook` | Immutable per-person debts built from events (Lent / Repaid / WrittenOff). `repay` returns `(newBook, incomePart)`: up to the debt closes it, only the excess is income. A write-off closes the debt, and lending again starts fresh. |

**Pattern: immutable books.** `LentBook` methods return a new book instead of mutating. That makes undo trivial: keep the old value.

---

## 4. State and actions in the app

### Preview store (`ui/demo/DemoStore.kt`), Built
- Compose snapshot state (`mutableStateListOf`, `mutableStateOf`), so screens recompose automatically.
- **Reads are derived, never stored:** `spentTodayPaise`, `monthSpentPaise`, `owedPaise` and `debtors` are computed from transactions and the `LentBook` through the `core` rules on every read.
- **Actions return the snackbar message.** Each is a method such as `setCategory`, `setKind`, `setExcluded`, `editDetails`, `addManual`, `learn`, `pickAccount`, `resolveTransfer`, `tagPayee`, `markRepaid` or `writeOff`.
- An injectable `clock: () -> LocalDate` keeps date logic testable.

### Undo pattern, Built
```kotlin
store.act(notifier) { setCategory(tx, "food", allLikeThis = true) }
// act = snapshot() → run action → show message with Undo → restore(snapshot)
```
A `Snapshot` captures transactions, Needs-you items, the `LentBook` and payee rules together, so one Undo reverts everything an action touched. This includes the side effects, such as a lent mark creating a debt.

### Real app, Planned (T6+)
Replace `DemoStore` with Room-backed repositories. The design principle is **raw SMS kept unchanged + user decisions → derived ledger**:

```
raw_sms (confirmed senders only) ─┐
templates · account answers ──────┤
payee rules · overrides ──────────┼──► derive (core rules) ──► transactions ──► totals / UI
merges · pairings · lent events ──┤
manual entries ───────────────────┘
```
- Undo becomes "delete the decision row and re-derive"; "All like this" becomes one rule row.
- When the parser improves, old messages are re-parsed for free.
- Screens don't change: routes bind the same state shapes.

---

## 5. SMS pipeline, Planned (T2–T18)

```
SMS ─► normalise sender (AD-CUBANK-S → CUBANK)
    ─► confirmed bank/wallet? ── no ──► label candidate (merchant/payment app) or ignore
    ─► learned template? ── yes ──► apply answer
    ─► regex parser ──► Transaction | Skipped(OTP, mandate, failed, scheduled, balance-only) | Unreadable → "Couldn't read"
    ─► account match: 4 digits direct; 3 digits only if exactly one account fits, else Needs you
    ─► label matching: UPI ref first, else amount + direction + ±30 min (never merges two bank messages)
    ─► wallet rules (balance = spend; wallet-UPI = label only)
    ─► self-transfer pairing (fee tolerance) ─► manual + SMS merge ─► lent repayment
    ─► derived ledger ─► limitStatus ─► Today · widget · alerts
```
- Each arrow is a pure function in `core` with table-driven tests and redacted fixtures.
- **Ingest is idempotent:** one SMS never creates two rows. A catch-up scan runs on each app open, because phone makers kill background receivers.
- **Template key = the full normalised template**, never a prefix.

---

## 6. UI patterns, Built

### Structure
| Layer | Role | Example |
|---|---|---|
| Shell | Navigation, FAB, overlays, snackbar host, sheet hosting | `SmsLedgerApp.kt` |
| Route | Binds store state and actions to one screen; owns no UI | `TodayRoute`, `NeedsRoute` in `Routes.kt` |
| Screen | **Stateless:** takes plain data + callbacks | `TodayScreen(state: TodayState, …)` |
| Component | Reusable, design-system level | `LimitHero`, `ConnectedChoice`, `NumberPad` |

- **State hoisting:** screens never touch the store, so they work unchanged on Room data.
- **Sheets float above everything:** `SheetState` (adding, editing transaction, teaching, editing limit) lives in the shell.
- **Callbacks are intents, not mutations:** e.g. `EditSheet(onKind, onCategory, onExcluded, onDetails)`, and Needs-you cards call a `NeedsActions` interface.
- **Navigation:** a 3-tab short navigation bar, plus full-screen overlays for Needs you and Owed. Back goes overlay → Today, then other tab → Today. There's no navigation library yet; simple enums are enough at this size.
- **Edge-to-edge:** Today owns its status-bar inset; the other tabs use top app bars that handle insets themselves.

### Design system
- `SmsLedgerTheme` wraps `MaterialExpressiveTheme` with **dynamic colour** on Android 12+ (brand scheme as fallback), `MotionScheme.expressive()`, and the Expressive type scale.
- **Status colours are fixed** (`LedgerColors`: warning, over, credit) and always paired with words.
- `material3` is pinned to **1.5.0-beta02** for Expressive components, which are wrapped in our own components (`ConnectedChoice`, `BigChoice`, `ShapeBadge`, `LedgerShapes`). Moving to stable means changing one version.
- Spacing comes from the `Space` scale; money text from `MoneyStyles` (tabular figures).

### Android surfaces
| Surface | How |
|---|---|
| Home-screen widget | Glance. The app **publishes** a `WidgetSnapshot` (SharedPreferences) whenever today's numbers change and calls `updateAll`; the widget only reads it and never computes rules. |
| Launcher shortcut | Static `shortcuts.xml` → `ACTION_ADD_EXPENSE` → `MainActivity` (`singleTop`) sets `pendingAdd` → the shell opens the Add sheet |
| Pin widget | `AppWidgetManager.requestPinAppWidget` from Settings |
| Splash, icon | core-splashscreen; adaptive icon with a monochrome layer |

---

## 7. Testing pattern

**Write the failing test first, make it pass, then run the full suite and the build.** Every task ends with one commit.

| Level | Where | Covers |
|---|---|---|
| Rule tests (most) | `core/src/test` | Money, limit, ledger maths, category rules, lent book. Plain JUnit, milliseconds each. |
| App logic tests | `app/src/test` | Store behaviour per spec rule (`DemoStoreTest`), meter geometry, search matching, widget snapshot, amount candidates |
| Corpus reports (Planned) | `tools/corpus` | Parser coverage on the real ~400 MB export |
| Device checks | Emulator | Every UI change is screenshotted in light and dark |

Conventions:
- Test names read as spec sentences, e.g. ``"money received from someone who owes you reduces the debt (spec 9)"``.
- Dates are injected (a `clock` parameter), never `LocalDate.now()` inside a rule.
- Fixtures are synthetic or hand-redacted. Real SMS are never committed (`data/` is gitignored).

Commands (with `JAVA_HOME` set to Android Studio's JBR; see the README):
```bash
./gradlew :core:test :app:testDebugUnitTest assembleDebug
```

---

## 8. Conventions

- **Kotlin style:** official. Comment density matches the surrounding code; KDoc says *why*, citing the spec section (`§9`).
- **Opt-ins:** Expressive, Material 3, layout and foundation experimental APIs are opted in once in `app/build.gradle.kts`, never per file.
- **Commits:** one per task or logical change, with a descriptive body and the Co-Authored-By trailer.
- **Privacy rules:**
  - no INTERNET permission;
  - `allowBackup=false`;
  - never log message bodies;
  - store only messages from confirmed senders.
- **Copy rules:** never show a negative balance; never use colour as the only signal; every automatic decision is reachable from a row tap.

---

## 9. Known gaps in the patterns (as of 24 Oct 2026)

- `DemoStore` is in-memory, so state resets on process death or rotation. Fixed by T6 (Room plus a ViewModel).
- Widget data is published only while the app runs, so there's no midnight rollover yet (T27).
- No navigation library and no dependency injection: fine at this size, revisit if screens or dependencies grow.
- Git line endings: Windows `autocrlf` converts files to CRLF. A `.gitattributes` (`gradlew text eol=lf`) is still needed before CI or Mac/Linux use.
