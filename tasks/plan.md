# Implementation Plan: SMS Ledger v0

Source spec: `sms-ledger-v0-features.md` (section numbers below, written §N, refer to it).
Full task details, acceptance criteria and verification steps: [`tasks/todo.md`](todo.md).

## Overview

A native Android app (Kotlin, on-device only) that reads bank SMS, works out which senders are banks and wallets, builds a spending ledger without double counting, and answers one question on the home screen with zero taps: **"₹X left today"**. Manual mode (no SMS permission) is a first-class path. The app ships on the Play Store under the "SMS-based money management" permission exception.

The project folder is empty, so this is a greenfield build. The plan goes after the two biggest risks first: **parser accuracy on real Indian bank SMS** and **Play Store approval of the SMS permission**. It then builds the app as thin vertical slices: manual mode first, then automatic capture, then the correctness rules (dedup, transfers, lent), then editing, history and notifications.

## Architecture Decisions

1. **Three Gradle modules.**
   - `core/`: pure Kotlin/JVM, no Android dependencies. Holds sender normalisation, bank/wallet classification, transaction parsing, templating, label matching, self-transfer pairing, lent maths and the daily-limit calculation. Its tests run in seconds on the JVM, and it is where most of the risk sits.
   - `tools/corpus/`: a JVM command-line tool that streams the ~400 MB SMS XML export through `core` and prints coverage reports. It never ships.
   - `app/`: Android code only, meaning Room, the SMS reader and receiver, WorkManager, notifications and Compose UI. It holds no business logic.
2. **Raw SMS are kept unchanged. The ledger is derived from them plus user decisions.** Store raw messages (id, sender, body, timestamp) once. Keep everything the user decides (account Mine/Not mine, templates, payee categories, per-transaction overrides, merges, pairings, lent marks) as separate rows. The transactions shown are computed from raw messages plus decisions. This makes the requirements "every decision is undoable" (§7, §8, §10) and "All like this" (§10) easy to meet: undo deletes the decision row and recomputes. It also lets us re-parse when the parser improves.
3. **Parsing order: learned template, then built-in regex, then "Couldn't read".** Nothing from a confirmed bank or wallet sender is dropped silently (§7). Template keys are the **full** normalised template.
4. **Stack:** Kotlin, Jetpack Compose with **Material 3 Expressive** (`material3` 1.5.0-beta02, pinned) and dynamic colour, Glance for the widget, Room, WorkManager, kotlinx-coroutines and Flow. Use manual constructor injection through one `AppContainer`. Hilt is not worth it at this size; we can add it later if needed. Money is stored as `Long` paise, never as floating point.
5. **Large-inbox import runs as a WorkManager job with progress shown.** It streams messages through a cursor in batches, so the first launch never blocks the UI.
6. **Privacy for test data.** The SMS export is **never committed**. Unit-test fixtures are hand-redacted samples (digits, names, VPAs and reference numbers replaced) kept in `core/src/test/resources/fixtures/`. The corpus tool reads the export from a path passed on the command line.

## Dependency Graph

```
Gradle skeleton (T1)
 ├── Corpus harness (T2) ── Sender discovery (T3) ── Txn parser (T4) ── Templatizer (T5)
 │                                                        │
 ├── Room schema + manual entry (T6) ── Daily limit/home (T7) ── Categories (T8)
 │                                                        │
 │        Permission + modes (T9) ── Inbox import + account confirm (T10)
 │                                         │
 │                    Ledger ingest from SMS (T11) ── Live capture (T12)
 │                                         │
 │                    Needs-you + Couldn't-read (T13) ── Template learning (T14)
 │                                         │
 │      Label matching (T15) ── Wallets (T16) ── Self-transfer (T17) ── Manual merge (T18) ── Lent (T19)
 │                                         │
 │      Row edit + undo (T20) ── All-like-this + bulk (T21) ── History (T22)
 │                                         │
 │      Limit alerts (T23) ── Lent reminder (T24)
 │
 └── Play policy prep (T0, early) ............................ Release (T25, T26)
```

## Task List (index)

### Phase 0: Foundation and policy risk
- [ ] T0: Play Store SMS policy pre-check and declaration draft
- [x] T1: Gradle project skeleton (app, core, tools/corpus)
- [ ] T2: Corpus harness: stream the XML export and print sender stats

### Checkpoint A: Foundation

### Phase 1: Parser on real data (highest technical risk)
- [ ] T3: Sender normalisation and bank/wallet/non-bank classification
- [ ] T4: Transaction extraction and skip rules
- [ ] T5: Templatizer

### Checkpoint B: Parser coverage review with a human

### Phase 2: Manual-mode vertical slice
- [ ] T6: Room schema v1, manual add expense, transaction list
- [ ] T7: Daily limit setting and home "₹X left today / ₹X over limit"
- [ ] T8: Categories, "not daily" flag, tag a transaction

### Checkpoint C: Usable manual-mode app

### Phase 3: Automatic mode
- [ ] T9: Permission onboarding and switching between Automatic and Manual
- [ ] T10: Inbox import, account discovery, confirmation screen
- [ ] T11: Ledger ingest from parsed SMS (with 3-digit matching rule)
- [ ] T12: Live capture through the RECEIVE_SMS receiver
- [ ] T13: "Needs you" queue and "Couldn't read" list
- [ ] T14: Template learning flow, plus view and undo templates

### Checkpoint D: Auto-capture end to end on a real phone

### Phase 4: Ledger correctness
- [ ] T15: Label-only matching (UPI ref, then amount + direction + ±30 min)
- [ ] T16: Wallets: paid from balance vs paid through wallet UPI
- [ ] T17: Self-transfer pairing with fee tolerance
- [ ] T18: Manual entry + bank SMS auto-merge (undoable)
- [ ] T19: Lent: mark, owed per person, repayment, partial, write-off

### Checkpoint E: Totals are correct on the corpus

### Phase 5: Edit, categorise, history
- [ ] T20: Tap-row edit of every field, with undo
- [ ] T21: "Just this one / All like this", sticky payee tags, bulk actions
- [ ] T22: History search, filters, month total

### Checkpoint F: Feature complete minus notifications

### Phase 6: Notifications
- [ ] T23: Daily-limit alerts at 80% and 100%, plus battery-optimisation prompt
- [ ] T24: Lent reminder on the 1st of the month

### Phase 7: Release
- [ ] T25: Privacy policy, Data Safety form, final permissions declaration, demo video
- [ ] T26: Release build, internal testing track, pre-launch report

- [ ] T27: Widget + launcher shortcut on real data, with midnight refresh
- [ ] T28: Large screens (navigation rail, list-detail History). After v0

### Checkpoint G: Ready for Play review

## Parallelization

- After T1, these can run in parallel: **T2–T5 (parser track, `core` + `tools`)** and **T6–T8 (app track, `app`)**. They only meet at T11, so agree the `ParsedTransaction` data class in `core` first.
- T0 (policy) is research and writing. It can run at any time but should finish before Phase 3 commits to the SMS design.
- Phase 4 tasks are pure `core` logic plus small UI hooks. T15/T16 and T19 can run in parallel. T17 and T18 both touch the pairing/merge decision tables, so run them one after the other.
- Room migrations must stay sequential. Only one task changes the schema at a time.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Google rejects the SMS permission declaration | **High**: the app cannot ship with auto-capture | T0 early: check the policy and draft the declaration. Manual mode works without SMS, so a rejected build can still ship as manual-only while we appeal. Make SMS clearly the core feature in the UI and store listing. |
| Parser misses or misreads messages on real banks | High | Measure on the 400 MB corpus (T2–T4) before any UI depends on it. The "Couldn't read" list plus template learning (T13/T14) means misses are visible, never silent. |
| Masked-digit rule wrongly classifies merchants as banks, or banks as non-banks | Med | Corpus report lists every classified sender for human review at Checkpoint B. The user confirms accounts on first run anyway (T10). |
| Double counting (label SMS, wallet UPI, transfers, manual + SMS) | High | Each rule is a pure `core` function with table-driven tests (T15–T18). Checkpoint E compares totals on the corpus by hand for a sample month. |
| OEM battery killers delay live capture and alerts | Med | Battery-optimisation exemption prompt (T23). On app open, run a catch-up re-scan of the inbox since the last processed id (T12). |
| Importing a large inbox is slow or runs out of memory | Med | Cursor streaming in batches inside WorkManager, with progress UI (T10). Time it against corpus-sized data. |
| Raw SMS leak through logs or crash reports | High (privacy) | No analytics or crash SDKs in v0. Lint rule or review check: never log message bodies. `allowBackup=false` or encrypted backup rules. |
| §16 layout is still pending | Med | T7 builds a minimal home screen that answers "₹X left today" with zero taps. The full layout is designed before Phase 5 polish (see Open Questions). |

## Open Questions

1. **Layout (§16):** flows are drafted in [`docs/flows.md`](../docs/flows.md), with decisions waiting for you in its §14. Once confirmed, T6–T22 follow it.
2. **Corpus:** where is the ~400 MB XML export, and is it from the "SMS Backup & Restore" app (`<sms address= body= date= …>` format)? Whose messages are they? This decides how strictly we redact fixtures.
3. **minSdk:** I propose 26 (Android 8.0). Is that OK?
4. **Defaults to confirm:** ±30 min label window, "a few hours" manual-merge window (proposal: 6 h), self-transfer window (proposal: 2 h) and fee tolerance (proposal: up to ₹50 or 2%, whichever is smaller).
5. **Day boundary:** does "today" reset at local midnight? (Assumed yes.)
6. **Play Console:** is a developer account already set up, and what are the app name and package id?
7. **Currency:** is v0 INR-only? (Assumed yes. Messages in other currencies go to "Couldn't read".)
