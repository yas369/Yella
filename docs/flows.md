# SMS Ledger v0: App Flows (Android · Material 3 Expressive)

Covers spec §16 (Layout) and the user-facing parts of §2–§15. Spec sections are written §N. This replaces the earlier platform-neutral flows and is the source of truth for the Compose UI.

> **Basis.** Android platform conventions, Material 3 Expressive guidance, Google Play policy and the v0 spec. UXKIN (real-app journeys) was **not** connected, so no real apps were compared and none are described here. Where this doc says "Android convention", it means platform or Material guidance, not an observed app.

---

## 0. Constraints

| Area | Constraint | Effect on flows |
|---|---|---|
| Platform | Android 8.0+ (minSdk 26), targetSdk 36, Jetpack Compose | Edge-to-edge is mandatory at target 35+; every screen draws behind the system bars and handles insets |
| Design system | **Material 3 Expressive** (`material3` 1.5.0-beta: `MaterialExpressiveTheme`, `MotionScheme.expressive()`, button groups, loading indicator, wavy progress, floating toolbar, `MaterialShapes`) | Expressive APIs are `@ExperimentalMaterial3ExpressiveApi`; we pin the version and wrap them in our own components |
| Colour | **Dynamic colour** (Material You) on Android 12+; brand scheme below 12. **Status colours are fixed** (amber = 80%, red = over) | The meaning of status colours never depends on the wallpaper |
| Back | **Predictive back** on (`enableOnBackInvokedCallback`) | Sheets, full screens and onboarding steps all preview the back gesture |
| SMS | Play **SMS-based money management** exception; **in-app prominent disclosure** shown *before* the runtime dialog | Disclosure screen is mandatory and its copy must match behaviour |
| Notifications | `POST_NOTIFICATIONS` runtime permission on Android 13+ | Asked in context, not during onboarding |
| Battery | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted by Play policy | We **don't** request it; we open the system's battery-optimisation list instead |
| Accounts | No accounts are created, so Play's account-deletion rule doesn't apply | We still offer **Delete all data**, found as easily as setup |
| Privacy | No INTERNET permission, no backup | Copy can truthfully say "can't use the internet" |

## 1. Android conventions this design follows

These are the platform patterns the flows are built on, so the app feels like it belongs on a Pixel or Samsung phone.

| Pattern | Android convention | How SMS Ledger uses it |
|---|---|---|
| Top-level navigation | **Navigation bar** (short, 3–5 destinations) on phones; **navigation rail** on tablets and foldables | 3 destinations: Today · History · Settings. A wide navigation rail at ≥ 600dp |
| Primary action | **FAB**, bottom-end; extended (icon + label) while content is at rest, collapses on scroll | "Add" extended FAB on Today, collapsing to an icon on scroll |
| Screen titles | **Large flexible top app bar** that collapses into a small bar on scroll | History, Settings, Needs you, Owed to you |
| Sub-screens | Top app bar with an **Up arrow**; predictive back | Needs you, Owed to you, Settings sub-pages |
| Quick tasks | **Modal bottom sheet** with a drag handle | Add expense, Edit, Teach, Daily limit, filters |
| Choosing one of a few | **Connected button group** (Expressive) | Mine / Not mine, Spent / Received, Money out / Money in |
| Feedback and undo | **Snackbar** with one action, above the nav bar | "Added ₹250 · Undo" everywhere |
| Search | **Search app bar → full-screen search view** with suggestions | History search |
| Date filters | **Date range picker** (modal) | History "Date" filter |
| Waiting | **Loading indicator** (shape-morphing) for short waits; **wavy progress** when progress is known | Import progress; first-frame loading |
| Settings | Grouped list sections in rounded containers, one switch per row | Settings and its sub-pages |
| Permissions | Rationale screen → system dialog → app keeps working if denied → after a second denial, link to app info | SMS, notifications |
| Outside the app | **Home-screen widget**, **launcher shortcuts**, notification actions, themed icon | Widget "₹X left today"; long-press shortcut "Add expense" (§12) |

## 2. App map

```
                 ┌──────────── first launch only ─────────────┐
Launch (splash) ─┤ Welcome → Daily limit → SMS disclosure      │
                 │   ├─ Allow → (system dialog) → Import → Is this yours? ─┐
                 │   └─ Manual → "You'll add expenses yourself" ───────────┤
                 └──────────────────────────────────────────────────┬───┘
                                                                     ▼
Navigation bar (3)   TODAY ─┬─ Needs you (banner + badge) ─ Teach a message (sheet)
                            ├─ Add (extended FAB → sheet)
                            ├─ Owed to you (card, only when > ₹0)
                            └─ Row → Edit (sheet)
                     HISTORY ─┬─ Search (expanded search view)
                              ├─ Filters: Date (range picker) · Account · Category · Type
                              └─ Row → Edit (sheet)
                     SETTINGS ┬─ Daily limit (sheet) · Mode · Accounts & wallets · Categories
                              ├─ Learned messages · Payee rules · Alerts · Battery
                              └─ Privacy policy · Delete all data

Outside the app:  Home-screen widget → Today  ·  Launcher shortcut "Add expense" → Add sheet
                  Limit alert notification → Today  ·  Lent reminder → Owed to you
```

---

## 3. Flow: First launch (Automatic)

**Goal:** a new user sees today's real remaining amount in under 2 minutes.
**Who / when:** every new install, once.
**Collects:** daily limit, SMS permission, and Mine / Not mine for each discovered account.

| # | Screen | Purpose | Main action | Back / out | Loading · error · empty |
|---|---|---|---|---|---|
| 0 | **Splash** (Android 12 splash API) | Themed app icon while the first frame loads | — | — | Held only until the first frame (no artificial delay) |
| 1 | **Welcome** | Show the product: a live-looking "₹340 left today" card with the day meter. One line: reads bank SMS, nothing leaves the phone. | **Get started** (large filled button) | Back exits the app | — |
| 2 | **Daily limit** | Set the number the day counts down from | Number pad; **Continue** | Predictive back → Welcome, value kept | Continue is disabled at ₹0 |
| 3 | **SMS disclosure** (Play prominent disclosure) | What's read, why, and what's kept; privacy policy link | **Allow SMS access** → system dialog. Secondary: **Add expenses myself** | Back → Daily limit | — |
| 4 | *System permission dialog* | — | Allow / Don't allow | "Don't allow" → §4 | — |
| 5 | **Reading your messages** | Import progress: **wavy progress bar**, count of messages checked, and banks appearing as they're found | None; continues on its own | Back sends the app to the background; import continues (WorkManager) with a progress notification | **Error:** permission revoked → Manual + banner. **Empty:** no bank senders → "No bank messages found" with **Use manual mode** / **Try again** |
| 6 | **Is this yours?** | One card per account or wallet, with its last transaction as evidence | Per card: connected button group **Mine \| Not mine**. Header: **All mine**. Bottom: **Continue** | Leaving early is allowed; unanswered cards go to Needs you | — |
| 7 | **Today** | First real view | — | — | §6 |

## 4. Flow: First launch (Manual) and permission edge cases

| Case | Behaviour |
|---|---|
| "Add expenses myself" or first "Don't allow" | One screen: **"You'll add expenses yourself"** → **Go to Today**; secondary **Allow SMS access instead** |
| Second "Don't allow" (Android stops showing the dialog) | "Allow SMS access" opens a **bottom sheet** with the steps and **Open app info** (`ACTION_APPLICATION_DETAILS_SETTINGS`). On return with the permission granted, continue to Import |
| Permission revoked while the app is in the background | On resume: switch to Manual, then a Today banner: "SMS access is off · **Turn on**" |

## 5. Flow: Mode switching (§2)

| From → To | Steps |
|---|---|
| Manual → Automatic | Settings → **Mode** → SMS disclosure → system dialog → Import → Is this yours? → Today. Manual entries stay; matching bank SMS merge with them (§8), with Undo |
| Automatic → Manual | Settings → **Mode** → dialog "Stop reading SMS? Transactions already captured stay." **Stop** / Cancel. Then a link to app info if the user also wants to revoke the OS permission |

---

## 6. Screen: Today

**Goal:** answer "how much can I still spend today?" with zero taps.

Layout (top to bottom). Today has **no top app bar title**; the hero is the title.

1. **Date line:** "Friday, 23 October" (label, muted).
2. **Hero:** "₹340" in an *emphasized* display style; "left today" under it. Over the limit it reads "**₹120** over limit" in the fixed red, never a negative number.
3. **Day meter:** thick Expressive-style progress track with a gap, a stop dot and a **marker at 80%**. Under it: "₹160 spent · Limit ₹500" (with "· 81%" once at or above 80%).
4. **Needs you banner** (only when there are items): a tonal card whose count sits in an Expressive **cookie shape**. "7 things need you · 2 couldn't read · 3 to tag". Tap → Needs you.
5. **One contextual card at a time** (each dismissable):
   - *Alerts*: shown the first day spending passes 50%. **Turn on** → `POST_NOTIFICATIONS` dialog. Not now → asked again after 7 days, at most twice.
   - *Battery*: only after alerts are on, in Automatic mode → opens the system battery-optimisation list.
   - *SMS access off*: if the permission was revoked.
6. **Owed to you card** (only when > ₹0).
7. **"Today" section** with the day's rows (§13 row anatomy).
8. **Month line:** "October · ₹18,240 spent" → History, filtered to this month.
9. **Extended FAB "Add"**, collapsing to an icon on scroll.

**States:** *Loading:* shape-morphing loading indicator in place of the hero, only if data takes > 300 ms. *Empty day:* "No spending yet today" under the meter. *Import still running:* a slim wavy progress line under the hero, with "Still reading older messages…".

## 7. Flow: Add expense

**Entry:** the FAB, the launcher shortcut "Add expense", or a widget tap on "+".
**Container:** modal bottom sheet, fully expanded.

| Element | Behaviour |
|---|---|
| Direction | Connected button group **Spent \| Received** (Spent selected) |
| Amount | Large readout and a custom number pad. Keys respond with haptic feedback and a quick shape squash |
| Where / from whom | One optional text field |
| Category | One scrolling row of filter chips (Spent only) |
| Save | Full-width filled button "**Save ₹250**"; disabled at ₹0. Closes the sheet; snackbar "Added ₹250 · **Undo**" |

**Back / out:** swiping down or predictive back with an amount typed shows a dialog "Discard ₹250?" **Keep editing** / **Discard**. With nothing typed, the sheet closes silently.

## 8. Flow: Needs you (§15.2)

**Container:** full screen with a **large flexible top app bar** ("Needs you", subtitle "7 to go · one tap each") and an Up arrow.

| Card | Question | One-tap answers |
|---|---|---|
| **Couldn't read** | The SMS shown as a message bubble, with candidate numbers highlighted | **Teach** (filled) · **Not a transaction** (outlined) |
| **Which account?** | "HDFC ••821 could be either" | One tonal button per account · **Neither is mine** |
| **Possible self-transfer** | "₹1,000 out of CUB ••1234 / ₹995 into Amazon Pay · 2 min apart" | **Self-transfer** · **Separate** |
| **New payee** | "MYNTRA · ₹1,299 · Yesterday" | One scrolling chip row of categories · **Lent** · **Skip** |
| **Tag in one go** | "Tag all 6 Swiggy payments" | Category chip row |
| **New account found** | "New: SBI ••9876" | Button group **Mine \| Not mine** |

Answered cards leave with an Expressive spring animation; snackbar with **Undo**. **Empty:** large check in a `MaterialShapes` container, "All caught up", **Back to Today**.

### 8a. Teach a message (§7)

Modal bottom sheet, two steps. Back steps back and keeps the earlier answer.

1. **"Is this money going out or coming in?"** Connected button group **Money out \| Money in**, then a text button **Neither, ignore messages like this**.
2. **"Which number is the amount?"** Each candidate number is a large chip (date fragments are excluded).
3. **Done:** "Got it. Messages like this will be read automatically." **Done** → snackbar "Learned · ₹1,450 spent · **Undo**". Other queued messages with the same template resolve at the same moment ("+3 more added").

## 9. Flow: Edit a transaction (§10)

Tap any row (Today or History) → modal bottom sheet.

- **Header:** amount (large), payee, account · time.
- **Quick actions:** a 3-button **connected toggle group**: **Lent** · **Self-transfer** · **Not a payment**. Lent and Self-transfer toggle on and off; Not a payment removes the row from totals and shows Undo.
- **Category:** chip row. Picking a chip shows an inline scope card: **All MYNTRA payments (3)** (filled) / **Just this one** (outlined). "Just this one" alone when there's only one payment.
- **Source message:** an expandable row showing the original SMS.
- **Merged rows** show a "Merged with your entry" chip with **Unmerge**.

## 10. Screen: Owed to you (§9)

Large flexible top app bar "Owed to you" with an Up arrow.

- **Header:** "₹2,500" large, "owed by 3 people".
- **One expandable card per person:** avatar (in a `MaterialShapes` container), name, handle, since; the outstanding amount on the right. Expanded: entries and repayments, "Repayments from rahul.s@okaxis are matched automatically", **Write off** (text, error colour) and **Mark repaid** (tonal).
- **Write off** → dialog "Write off ₹600 from Ravi? It won't count as owed or appear in reminders." → snackbar with **Undo**.
- **Empty:** "No one owes you anything."

## 11. Screen: History (§14)

- **Search app bar** at the top ("Search payee or amount"), hiding on scroll and returning on the first scroll up. Tapping it opens the **full-screen search view** with frequent payees as suggestions and live results; back closes it.
- **Month header** at the top of the list: "October 2026 · ₹24,997 spent".
- **Filter chips:** **Date** (modal date range picker) · **Account** · **Category** · **Type** (each opens a menu). Active chips are filled and show their value.
- **List:** grouped by day; each day header stays at the top while scrolling and shows the day total.
- **Tablets and foldables (≥ 840dp), after v0:** list on the left, the selected transaction's detail on the right (list-detail), instead of the sheet.
- **Empty:** "Nothing here yet." **No results:** "No matches for 'swiggy'" with **Clear filters**.

## 12. Outside the app (Android-only surfaces)

| Surface | Behaviour | Status |
|---|---|---|
| **Home-screen widget** (Glance) | 2×1: "₹340 left today" plus a thin meter. 4×2: adds the last 3 rows and a **+** button. Uses dynamic colour; tap → Today | **Proposed for v0**: directly serves "check with zero taps" |
| **Launcher shortcut** | Long-press the icon → **Add expense** → opens the Add sheet | Proposed for v0 (cheap) |
| **Themed icon** | Monochrome adaptive icon layer for Android 13+ themed icons | Proposed for v0 |
| **Limit notifications** | "80% of today's limit used · ₹100 left" / "Daily limit reached · ₹40 over" → Today | From the spec |
| **Lent reminder** | "₹2,500 owed to you by 3 people" on the 1st → Owed to you | From the spec |
| **New-payee notification with actions** | "MYNTRA ₹1,299 · Tag it" with up to 3 category actions | **Optional, off by default.** It would add notifications beyond the spec's minimal set |

## 13. Screen: Settings (§11)

Large flexible top app bar "Settings". Grouped sections in rounded containers.

| Group | Rows |
|---|---|
| Spending | **Daily limit** (sheet) · **Categories** (sub-page: per-category "Counts toward daily limit" switch, add/rename) · **Owed to you** |
| Messages | **Mode** (Automatic / Manual) · **Accounts & wallets** (Mine/Not mine per row) · **Learned messages** (list; **Forget** returns messages to Couldn't read) · **Payee rules** |
| Alerts | **Limit alerts** switch · **Lent reminder** switch · **Battery optimisation** (opens the system list; shows current status). If the OS notification permission is off, rows show "Off in system settings · **Open**" |
| Home screen | **Add widget to home screen** (asks the launcher to pin the widget; if it can't, explains long-press → Widgets) |
| Appearance | **Dynamic colour** switch (Android 12+; default on) · **Theme**: connected button group System / Light / Dark |
| Privacy | **Privacy policy** · **Delete all data** (dialog; wipes and returns to Welcome) |
| Design preview | Today state switcher, replay onboarding. **Debug builds only** |

## 14. Shared anatomy and rules

**Transaction row:** a 40dp avatar (initial, or an icon for transfers and income) · payee · "account · time" · amount on the right with a tag under it (category / Untagged · / Lent / Transfer / Received). Lent rows are dimmed **and** labelled. Income shows "+₹" in the credit colour.

**Rules:**
- **One tap per decision.** Primary decisions use full-width buttons, 56dp tall or more; every tap target is 48dp or more.
- **Undo** everywhere: a snackbar (≈ 5 s) plus a permanent route in Settings for templates, accounts and rules.
- **Predictive back** on every sheet, full screen and onboarding step. Nothing intercepts back except the add-expense discard prompt.
- **Motion:** `MotionScheme.expressive()` springs for state changes; cards leave the queue with a spring; the hero amount animates when it changes.
- **Haptics:** key taps, toggles, and crossing 80% or 100% when a new transaction lands while the app is open.
- **Money formatting:** Indian grouping (₹1,23,456). Totals are rounded to whole rupees; single transactions are exact.
- **Accessibility:** the hero is announced as one phrase ("340 rupees left today"); status never relies on colour alone; layouts are checked at 200% font scale; TalkBack labels on every icon button.
- **Light and dark** are both first-class, with dynamic colour in both.

## 15. Decisions

| # | Decision | Proposal | Status |
|---|---|---|---|
| A1 | Navigation | 3-item short navigation bar; wide navigation rail on large screens | Proposed |
| A2 | Needs you placement | Banner on Today plus a nav badge, not a 4th destination | Proposed (carried over) |
| A3 | Onboarding order | Limit before the SMS ask | Proposed (carried over) |
| A4 | Notification ask | First day spending passes 50% | Proposed (carried over) |
| A5 | Account confirmation | Can leave early; the rest go to Needs you | Proposed (carried over) |
| A6 | Default limit | ₹500 suggested | Proposed (carried over) |
| A7 | Dynamic colour | On by default (Android 12+), switch in Settings; status colours fixed | **Decided** (you chose full Expressive) |
| A8 | Material 3 1.5.0-beta | Pinned `1.5.0-beta02`, Expressive APIs wrapped in our components | **Decided**: move to stable 1.5.0 when it ships |
| A9 | Home-screen widget + launcher shortcut + themed icon | Add to v0 | **Decided**: built in the preview; real-data wiring is T27 |
| A10 | Battery optimisation | Open the system list, don't request the restricted permission | Proposed (Play policy) |
| A11 | Tablet / foldable layout | Navigation rail plus list-detail History | **Decided: after v0** (T28) |
