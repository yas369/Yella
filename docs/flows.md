# SMS Ledger v0: App Flows

Covers spec §16 (Layout, pending) and the user-facing parts of §2–§15. Spec sections are written §N.
**Basis:** Android Material 3 conventions, Play Store SMS policy and the v0 spec. This was **not** compared against real apps' journeys, because UXKIN wasn't connected.

## 0. Design goals

| Goal | Consequence |
|---|---|
| "How much can I still spend today?" answered with **zero taps** | The app opens on Today, with the number as the largest thing on screen. |
| Show value before asking | Onboarding shows what the app does and sets the limit **before** asking for SMS permission. |
| Ask for each permission when it's needed | SMS during onboarding (it is the core feature). Notifications later, when an alert would actually help. Battery optimisation only after alerts are on. |
| One "Needs you" queue | Every uncertain item (§15.2) lives in one place, with one tap per decision. |
| Never trap the user | Every optional step can be skipped. Back keeps what was entered. Every automatic decision can be undone. |

## 1. App map

```
                       ┌──────────── first launch only ────────────┐
Launch ──► Onboarding ─┤ Welcome → Daily limit → SMS disclosure    │
                       │   ├─ granted → Import → Confirm accounts ─┼─► TODAY
                       │   └─ denied  → Manual mode ───────────────┘
                       └───────────────────────────────────────────┘

Bottom navigation (3 tabs):
  TODAY ─────┬── Needs you queue (banner, count badge) ── Teach a message
             ├── Add expense (FAB → bottom sheet)
             ├── Owed to you (card, shown only when > ₹0)
             └── Transaction row → Edit sheet
  HISTORY ───┬── Search / filters
             └── Transaction row → Edit sheet
  SETTINGS ──┬── Daily limit · Mode · Accounts & wallets · Categories
             ├── Learned templates · Notifications · Battery
             └── Privacy policy · Delete all data
```

**Why three tabs:** Today covers the main question, History covers looking back, and Settings covers control. "Needs you" is not a tab. It appears as a banner on Today only when it has items, so the queue doesn't permanently take space. Its count is also shown as a badge on the Today tab icon.

## 2. Flow: First launch (Automatic path)

**Goal:** a new user sees today's real remaining amount in under 2 minutes.

| # | Screen | Purpose | Main action | Back / out | Loading · error · empty |
|---|---|---|---|---|---|
| 1 | **Welcome** | One sentence plus a sample "₹340 left today" visual: "Reads your bank SMS on this phone. Nothing leaves your device." | **Get started** | Back exits the app | — |
| 2 | **Daily limit** | Set the number the app counts down from. Large number pad, prefilled with a suggestion (₹500); quick chips ₹300 / ₹500 / ₹1,000. | **Continue** | Back → Welcome, value kept | Must be > 0; inline hint if 0 |
| 3 | **SMS disclosure** (Play prominent disclosure) | Plain statement of what is read (bank and wallet SMS), why (to build the ledger), and that it never leaves the phone. Link to the privacy policy. | **Allow SMS access** → system dialog. Secondary: **Add expenses myself** (Manual path, §3) | Back → Daily limit | — |
| 4 | *System permission dialog* | — | Allow / Deny | Deny → §3 | — |
| 5 | **Reading your messages** | Import progress: "Found 4 banks · 1,240 transactions…" with a determinate bar. Messages are read in batches. | None needed. The screen continues on its own when done. | Back sends the app to the background; import keeps running (WorkManager), with a progress notification | **Error:** permission revoked mid-import → go to Manual with a banner. **Empty:** no bank senders found → screen "No bank messages found" with options to switch to Manual or retry. |
| 6 | **Is this yours?** (account confirmation, §4) | One card per discovered account (`CUB · 1234`) or wallet (`Amazon Pay`), showing its last transaction as evidence. | Per card: **Mine** / **Not mine**. Header bulk action: **All mine**. Then **Done** (enabled once every card is answered) | Back → keeps answers; leaving is allowed, and unanswered cards go to Needs you | **Empty:** only possible if step 5 found nothing, which is handled there |
| 7 | **Today** | First real view: "₹X left today". | — | — | See §5 |

Not asked during onboarding: categories, notifications, battery. Each appears later when it becomes relevant (§5 cards).

## 3. Flow: First launch (Manual path)

Starts when the user taps "Add expenses myself" or denies the system dialog.

| # | Screen | Purpose | Main action | Back / out | States |
|---|---|---|---|---|---|
| 1 | **Manual mode** (one screen) | "You'll add expenses yourself. You can turn on automatic reading any time in Settings." | **Go to Today** | Back → SMS disclosure (can try again) | — |
| 2 | **Today** (empty) | Shows "₹500 left today" and a large **Add expense** button. | **+ Add** | — | Empty: "No spending yet today" |

**Permission permanently denied** (Android stops showing the system dialog after the second deny): "Allow SMS access" opens a sheet explaining the steps, with **Open app settings**. When the user returns with the permission granted, continue to Import (§2 step 5).

## 4. Flow: Mode switching (§2)

| From → To | Steps | Notes |
|---|---|---|
| Manual → Automatic | Settings → Mode → SMS disclosure → system dialog → Import → Confirm accounts → Today | Existing manual entries stay. The §8 merge rule pairs them with matching bank SMS, and undo is available. |
| Automatic → Manual | Settings → Mode → dialog: "Stop reading SMS? Transactions already captured stay in your ledger." **Stop** / Cancel | The app can't revoke the OS permission itself. The dialog links to app settings if the user also wants to revoke it. |
| Permission revoked outside the app | On next resume: switch to Manual, then show a Today banner: "SMS access is off. Turn on." | No data is lost. |

## 5. Screen: Today (home)

Top to bottom:

1. **Hero:** "**₹340** left today" in display size, with a thin progress bar. Over the limit, it reads "**₹120** over limit" in an error colour and the bar is full. Never a negative number (§12).
2. **Needs you banner** (only when the count is above 0): "**5** need you · 2 couldn't read". Tap → Needs you queue.
3. **One-time contextual cards** (one at a time, each dismissable):
   - *Alerts*: shown the first day spending passes 50%: "Get a heads-up at 80% and 100%?" **Turn on** → system notification dialog (Android 13+). Not now → shown again after 7 days, at most twice.
   - *Battery*: only after alerts are on, in Automatic mode: "Some phones stop apps in the background. Exempt SMS Ledger so totals stay live." **Allow** → system dialog.
   - *Categorise*: once 5 or more payees are untagged: "Tag your top payees so rent and bills don't count against today." → Needs you, filtered to new payees.
4. **Owed to you card** (only when > ₹0): "₹2,500 owed by 3 people" → Owed screen.
5. **Today's transactions:** rows show payee, account, time and amount. Transfers carry a ⇄ badge, lent rows are grey, and uncategorised rows have a subtle dot. Tap → Edit sheet (§8).
6. **Month line:** "October: ₹18,240 spent" with no target (§12). Tap → History filtered to this month.
7. **FAB "+":** Add expense (§6).

**States:** *Loading:* hero skeleton for under 300 ms (the data is local). *Empty day:* "No spending yet today" under the hero. *Import still running:* a slim progress strip under the hero ("Still reading older messages…"), with the hero already live.

## 6. Flow: Add expense (manual entry)

Bottom sheet opened from the FAB. One screen, no form.

| Element | Behaviour |
|---|---|
| Amount | Large display with a custom number pad. The pad has focus as soon as the sheet opens. |
| Direction | Segmented control **Spent** (default) / **Received** |
| Category | A row of chips for the most-used categories. Optional. |
| Payee / note | One optional text field: "Where? (optional)" |
| Save | Saves, closes the sheet, the hero updates immediately, and a snackbar says "Added ₹250 · Undo" |

**Back / out:** swiping down or pressing back with an amount entered asks "Discard ₹250?" Keep / Discard. With nothing entered, the sheet closes silently.
**Error:** Save is disabled at ₹0.

## 7. Flow: Needs you queue (§15.2)

A full-screen list grouped by type, with the biggest groups first. Each card has large one-tap answers and a **Later** text button.

| Card type | Question | One-tap answers |
|---|---|---|
| **Couldn't read** | The message is shown with its numbers highlighted | **Teach** (→ §7a) · **Not a transaction** |
| **Which account?** (3-digit ambiguity, §4) | "HDFC ·234: which one?" | `HDFC 1234` · `HDFC 5234` · **Not mine** |
| **Possible transfer** (§9) | "₹1,000 out of CUB, ₹995 into Amazon Pay, 2 min apart" | **Self-transfer** · **Separate payments** |
| **New payee** (§11) | "MYNTRA · ₹1,299" | Category chips (Food / Shopping / …) · **Lent** · **Skip** |
| **Bulk** (§15.5) | "Tag all 6 Swiggy payments" | Category chips → applies to all 6, with an Undo snackbar |
| **New account found** (later SMS from an unknown bank or digits) | "New: SBI · 9876" | **Mine** · **Not mine** |

After each answer the card animates out and the next one moves up. **Empty state:** "All caught up", with a button back to Today. Back → Today; unanswered items stay in the queue.

### 7a. Teach a message (template learning, §7)

Steps are shown in a bottom sheet over the message. Each step is a single tap.

1. **Is this a payment or a received amount?** **Money out** · **Money in** · **Neither (ignore messages like this)**
2. **Which number is the amount?** Each number in the message becomes a tappable chip, e.g. `500.00` · `1234` · `12,340.50`. If the message has only one candidate, this step is skipped automatically.
3. *(Only if the account can't be resolved)* **Which account?** Account chips.
4. **Done:** "Got it. Messages like this will be read automatically." The transaction appears in the ledger. Any other queued messages with the same template resolve at the same moment, with a count shown ("+3 more added").

Back moves one step back and keeps earlier answers. Learned templates can be undone in Settings → Learned templates (§10).

## 8. Flow: Edit a transaction (§10)

Tap any row (Today or History) → bottom sheet.

- **Top:** amount, payee, account, time. Each one can be tapped and edited in place.
- **Quick actions (big buttons):** **Category** · **Lent** · **Self-transfer** · **Not a transaction**
- **Details:** direction, type, note, and the source SMS (read-only, expandable).
- **Scope prompt** (only when the change could apply more widely, such as category, type or "Not a transaction" for a templated or repeat payee): an inline choice inside the sheet, **Just this one** / **All like this (12)**.
- **Result:** the sheet closes, totals recompute, and a snackbar says "Updated · Undo".
- **Merged manual + SMS rows** show a "Merged with your entry" chip with **Unmerge** (§8).

**Lent sub-flow (§9):** tap **Lent**. If the transaction has a VPA or phone number, it's done immediately (row turns grey, snackbar with Undo). If it has neither, ask once: "Who did you lend to?" with a name or phone field and suggestions from recent payees.

## 9. Screen: Owed to you (lent, §9)

- One list row per person: name or VPA, outstanding ₹, and "since 12 Sep".
- Tap → person detail: lent entries plus repayments matched automatically ("₹400 repaid 3 Oct"), and the outstanding amount.
- **Write off** button on each entry → dialog "Write off ₹600 from Ravi? It won't count as owed or appear in reminders." Cancel / **Write off**; snackbar with Undo.
- **Manual repayment** (cash): "Mark ₹ as repaid" with the number pad.
- **Empty:** "No one owes you anything."
- **Entry points:** Today card, the monthly reminder notification, Settings.

## 10. Flow: History (§14)

- **Top:** search field ("Payee or amount") and a row of filter chips: **Date** · **Account** · **Category** · **Type**. Each chip opens a small picker sheet; active chips are filled.
- **List:** grouped by day, with a sticky month header showing "₹18,240 spent".
- **Tap a row** → Edit sheet (§8).
- **Empty (no data):** "Nothing here yet." **No results:** "No matches for 'swiggy'", with **Clear filters**.
- **Loading:** paged; skeleton rows while the next page loads.

## 11. Screen: Settings

| Item | Opens | Notes |
|---|---|---|
| Daily limit | Number pad sheet | Applies from now on; today's figure recomputes immediately |
| Mode | §4 flow | Shows "Automatic" or "Manual" |
| Accounts & wallets | List with a Mine / Not mine toggle per row | Changing an answer recomputes totals; Undo snackbar |
| Categories | List with a **Counts toward daily limit** switch per category; add, rename | "Not daily" in the spec is shown as this switch turned off, which reads more clearly |
| Learned templates | List: example message, answer, and how many transactions it produced | Swipe or tap → **Forget**. Its transactions return to "Couldn't read" (§7) |
| Payee rules | Payee → category list | Remove a rule |
| Notifications | Switches for limit alerts and the lent reminder | If the OS permission is off, the row shows "Off in system settings" with **Open** |
| Battery optimisation | Status plus a button to the system dialog | Automatic mode only |
| Privacy policy | Opens in the browser | Required for Play |
| Delete all data | Confirm dialog (type "DELETE" or hold to confirm) | Wipes the database and returns to onboarding. Even with no accounts, it is the honest counterpart to setup. |

## 12. Notifications (§13)

| Notification | When | Tap goes to | Actions |
|---|---|---|---|
| "80% of today's limit used · ₹100 left" | First crossing of 80% each day | Today | — |
| "Daily limit reached · ₹40 over" | First crossing of 100% each day | Today | — |
| "₹2,500 owed to you by 3 people" | 1st of the month, if anything is outstanding | Owed to you | — |
| "Reading your messages… 62%" | During the first import (ongoing, low priority) | Import screen or Today | — |

## 13. Cross-cutting rules

- **Undo** everywhere uses a snackbar (about 5 s) plus a permanent route in Settings (templates, accounts, rules).
- **Back gesture** always moves one level up. No screen intercepts it except the add-expense discard prompt.
- **Large tap targets:** at least 48 dp. Primary decisions use full-width buttons.
- **Money formatting:** Indian grouping (₹1,23,456), with no decimals unless non-zero.
- **Accessibility:** the hero is announced as a single phrase ("340 rupees left today"); grey lent rows also carry a "Lent" label, so colour is not the only signal.
- **Dark mode** is supported from v0.

## 14. Decisions for you

1. **Bottom navigation with 3 tabs (Today / History / Settings), with Needs you as a banner rather than a tab.** Alternative: make Needs you a 4th tab. That makes it easier to find, but a tab with nothing in it is wasted most of the time.
2. **Daily limit before the SMS permission in onboarding.** Value first; it costs one extra screen before the permission.
3. **Notification permission asked the first day spending passes 50%**, not during onboarding. Alerts are less likely to be turned on early, but the request makes far more sense at that moment.
4. **"Is this yours?" must be answered for every card before Done is enabled, or the user can leave early and unanswered cards go to Needs you.** Proposal: allow leaving early.
5. **Suggested default limit** (₹500), or start empty?
