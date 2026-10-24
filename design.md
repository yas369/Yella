# SMS Ledger — Design brief

This file is the visual and UX rulebook for the app. Every UI change must follow it. When a screen doesn't match this brief, the screen is wrong, not the brief.

Companion to `sms-ledger-v0-features.md` (what the app does). This file covers how it looks and feels.

---

## 1. Design direction

**A ledger, not a dashboard.** The app should feel like a well-kept account book: calm, precise, dense enough to scan, with numbers you trust at a glance.

Three words to judge every screen by:
- **Precise:** numbers line up, amounts are exact, nothing is vague.
- **Quiet:** colour and emphasis only where something needs attention.
- **Honest:** show the evidence (the real SMS), never hide uncertainty.

---

## 2. Data rules (non-negotiable)

- **No sample, seed, demo or placeholder data anywhere in the shipped app.** No fake names, merchants, accounts or amounts in any build variant shown to users.
- Every number and name on screen comes from the user's own SMS or the user's own manual entries.
- A fresh install with no data must show **empty states** (section 9), not fake content.
- All parsing, matching and dedup runs in the background. The UI only displays results and asks for decisions.
- Previews and screenshots for development must use clearly synthetic data that lives **only** in preview/test code, never in the app's runtime data.

---

## 3. Typography

**Font family: IBM Plex Sans** for all UI text. **IBM Plex Mono** only for raw SMS excerpts.
- Bundle the fonts with the app (no runtime download), so they render identically offline.
- Do **not** use the system default font, Roboto or Inter.

**Two weights only:** Regular (400) and SemiBold (600).

**Type scale (sp):**

| Role | Size | Weight | Use |
|---|---|---|---|
| Hero number | 48 | SemiBold | "₹160 left today" amount, one per screen max |
| Title | 20 | SemiBold | Screen titles, section headers |
| Body | 16 | Regular | Row primary text, card content |
| Amount | 16 | SemiBold | Amounts in rows |
| Label | 14 | Regular | Secondary row text, button text |
| Caption | 12 | Regular | Timestamps, hints, SMS excerpts |

**Money formatting**
- Always **tabular (fixed-width) numerals** for any amount, so columns align.
- Indian grouping: ₹1,00,000, not ₹100,000.
- Always the ₹ symbol, no space: ₹1,299. Never "Rs", "INR" or "1299.0".
- Paise only when non-zero: ₹1,299 and ₹1,299.50, never ₹1,299.00.
- Amounts right-aligned in lists.
- Debits shown plain; credits prefixed with "+" in the credit colour. Never use a minus sign for debits.

**Screen titles** are 20sp, not display size. No oversized headers that push content down.

---

## 4. Colour

**Material You dynamic colour is OFF.** The app uses its own fixed palette. Both light and dark themes are supported; follow the system setting.

### Tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | #F7F5F0 | #121417 | Screen background (warm paper / ink) |
| `surface` | #FFFFFF | #1B1E22 | Cards and sheets |
| `text` | #1B1C1E | #E8E6E1 | Primary text |
| `text-secondary` | #5E6166 | #9A9C9F | Secondary text, captions |
| `divider` | #E3E0D8 | #2A2E33 | List row dividers, card borders |
| `accent` | #2F45B5 | #8FA2F2 | Primary action, selected state, links |
| `on-accent` | #FFFFFF | #0E1430 | Text on accent fills |
| `over` | #C2410C | #F08A5D | Over limit |
| `near` | #B7791F | #E2B04A | Near limit (80%+) |
| `credit` | #2E7D4F | #6CC48F | Credit amounts |
| `lent` | #8A8D91 | #6E7176 | Lent rows (grey) |

### Colour rules
- **Colour means state, never decoration.** If something is coloured, the user should be able to say why.
- **Accent** is used only for the primary action on a screen and for selected states.
- The **hero number** is `text` normally, `near` at 80%+, `over` past the limit.
- **Lent** rows: text and amount in `lent` grey, plus a small "Lent" label. Never rely on colour alone.
- No gradients, glows, coloured shadows or tinted card backgrounds.

---

## 5. Spacing, shape, elevation

- **Spacing scale (dp):** 4, 8, 12, 16, 24, 32. No other values.
- **Screen side margin:** 16dp.
- **List row height:** 56dp minimum (single line + caption).
- **Corner radius:** cards 12dp, buttons 8dp, chips 8dp, inputs 8dp. List rows have no radius.
- **Elevation:** none. Separate things with `divider` lines or a `surface` vs `bg` contrast, never drop shadows.
- **Touch targets:** 48dp minimum.

---

## 6. Components

### List rows (the default container)
- Lists (home recent, history, owed to you, accounts) are **rows separated by 1dp dividers**, not cards.
- Row layout: left = payee (Body) with caption below (account · time); right = amount (Amount, tabular, right-aligned).
- Tap a row to open edit. No visible chevrons or edit icons on every row.

### Cards (only for decisions)
- **Cards are used only for items that need a user decision** (Needs-you items). Nowhere else.
- Every decision card shows:
  1. What kind of decision (Caption, `text-secondary`), e.g. "Which account?"
  2. The transaction: amount + payee + time
  3. **The SMS excerpt** that triggered it, in Plex Mono Caption, one or two lines, with the key part visible (e.g. "…debited from A/c XX821 to SWIGGY Ref 4521…")
  4. The actions

### Buttons
- **Primary:** filled `accent`, 8dp radius, 48dp tall. **At most one per card or screen.**
- **Secondary:** outlined (1dp `divider` border) or plain text button in `accent`.
- **Destructive / dismissive** ("Neither is mine", "Not a transaction", "Skip"): plain text button in `text-secondary`.
- No pill-shaped (fully rounded) buttons.
- Stacked full-width buttons are only used when the options are equal choices (e.g. picking between two accounts); otherwise use side-by-side.

### Category chips
- Chips **wrap onto multiple lines**. Never a horizontally scrolling row that hides options off-screen.
- Chips: 8dp radius, outlined, Label text. Selected = filled `accent`.
- Show the user's most-used categories first.
- "Lent" and "Self-transfer" chips are visually separated from spending categories (after a small gap), because they change how money is counted.

### Progress (limit)
- Thin bar (4dp), full width, `divider` track, fill in `text` / `near` / `over` by state.
- No circular rings or gauges.

### Icons
- One icon set only: **Material Symbols Outlined**, 20dp in rows, 24dp in the app bar.
- Icons only where they carry meaning (direction of money, alert). No decorative icons.

### Motion
- 150–200ms, standard easing. No bounce, spring or playful animations.
- Resolved Needs-you cards slide out; the count updates.
- Undo is offered via a snackbar for 5 seconds after any decision or edit.

---

## 7. Screens

### Home
Order, top to bottom:
1. **"₹X left today"**: hero number. Below it in Label: "₹Y of ₹Z spent". Then the limit bar. Over limit: "₹X over limit" in `over`.
2. **This month:** one line, "₹N spent in October" (Body). No target, no bar. There is no monthly limit.
3. **Needs-you entry:** a single row, "N to review", with a small count badge. Hidden entirely when N = 0.
4. **Recent transactions:** last ~10 rows (list rows), then "See all" (text button) to History.

No other widgets on home.

### Needs you
- Title: "To review" (20sp) with the count.
- A vertical list of decision cards (section 6).
- Similar items are grouped with a bulk action, e.g. "6 payments to SWIGGY · Tag all as Food".
- Empty: "Nothing to review." and nothing else.

### History
- Search field at top (payee or amount).
- Filter row below: date range, account, category, type. Filters wrap; active filters show as filled chips with an ×.
- Rows grouped by day with a day header: date (Label) and the day's total spent (Amount, right).

### Owed to you
- Header line: "₹X owed by N people" (Body).
- Rows: person (name or VPA/phone), "since [date]" caption, amount right.
- Tap a row to see that person's lent entries and repayments, with two actions: **Mark repaid** and **Write off**.
- Written-off and repaid entries leave this list.

### Couldn't read
- List of unreadable SMS from confirmed senders: sender, time, SMS excerpt (Plex Mono).
- Tap: a sheet where the user taps the amount in the message, then picks debit / credit / not a transaction, then "Just this one" or "All like this".
- Learned templates are viewable and undoable in Settings.

### First run
1. **Permission explanation screen** before the system SMS prompt: plain explanation of what's read, that nothing leaves the phone, and that manual mode works without it. (Also required by Play Store prominent-disclosure rules.)
2. **Account confirmation:** list of discovered accounts and wallets as rows, each with "Mine" / "Not mine" toggles. One "Done" primary button.
3. **Set daily limit:** one numeric field, one button.
4. Battery-optimisation prompt only on phones/brands where it's needed, with one sentence explaining why.

### Manual entry
- Opens with the amount field focused and the numeric keyboard up.
- Defaults: today's date, debit, last-used account. The user only changes what's different.
- Category chips below. Save is one primary button.

### Settings
- Daily limit, mode (automatic / manual), accounts, categories (with "not daily" toggle), learned templates, notifications, privacy policy.

---

## 8. Copy

- **Plain and short.** "5 to review", not "5 to go · one tap each".
- **Say what happened, with numbers.** "₹1,000 out of CUB ••1234 and ₹995 into Amazon Pay, 2 min apart."
- No exclamation marks, emoji, jokes or cheerleading ("Great job!", "You're on fire").
- No financial advice or judgement ("You're spending too much on food").
- Account masks always written as "••1234".
- Dates: "Today", "Yesterday", then "12 Sep". Year only when not the current year.

---

## 9. Empty and error states

Every list has an empty state: one line of `text-secondary`, no illustration.

| Screen | Empty text |
|---|---|
| Home recent | "No transactions yet. They'll appear here as bank SMS arrive." |
| Home (manual mode) | "No transactions yet. Tap + to add one." |
| To review | "Nothing to review." |
| History search | "No transactions match." |
| Owed to you | "Nobody owes you anything." |
| Couldn't read | "Every bank SMS was read." |

- SMS permission denied: a single row on home, "Automatic capture is off · Turn on", tapping to the permission explanation screen.
- Notifications denied: a row in Settings only; never nag on home.

---

## 10. Accessibility

- Text contrast at least 4.5:1 against its background in both themes.
- The UI must work at 200% system font size without clipping amounts (amounts wrap below the payee if needed).
- Never use colour as the only signal (lent has a label, over-limit has the word "over").
- Every icon-only button has a content description.

---

## 11. Never do

- Sample, seed or demo data in the shipped app
- Material You dynamic colour
- Pill-shaped buttons
- Cards for list rows
- Display-size screen titles
- Horizontally scrolling chip rows that hide options
- More than one primary (filled) button per card or screen
- Gradients, shadows, glows, glassmorphism
- Circular progress rings for the limit
- Decorative illustrations, emoji, celebratory animations
- Amounts without tabular numerals, or formatted as "Rs" / "1299.0"
- A decision card without the SMS excerpt that triggered it
