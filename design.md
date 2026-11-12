# SMS Ledger — Design brief

This file is the visual and UX rulebook for the app. Every UI change must follow it. When a screen doesn't match this brief, the screen is wrong, not the brief.

Companion to `logic.md` (what the app does). This file covers how it looks and feels.

**Revised 2026-11-12:** the look moved from the paper-ledger style to an Apple-style (iOS "inset grouped") look, at the owner's request. Behaviour, wording and money formatting did not change.

---

## 1. Design direction

**Calm, clear and familiar.** The app should feel like a well-made iPhone system app: a light grey page, white rounded sections, large bold titles, one blue accent, and nothing that needs explaining.

Three words to judge every screen by:
- **Precise:** numbers line up, amounts are exact, nothing is vague.
- **Quiet:** colour and emphasis only where something needs attention.
- **Honest:** show the evidence (the real SMS), never hide uncertainty.

---

## 2. Data rules (non-negotiable)

- **No sample, seed, demo or placeholder data anywhere in the shipped app.** No fake names, merchants, accounts or amounts in any build variant shown to users.
- Every number and name on screen comes from the user's own SMS or the user's own manual entries.
- A fresh install with no data must show **empty states** (section 9), not fake content.
- Reading and sorting messages runs off the main thread. The UI only displays results and asks for decisions.
- Previews and screenshots for development must use clearly synthetic data that lives **only** in preview/test code, never in the app's runtime data.

---

## 3. Typography

**Font family: Inter** for all UI text, bundled as one variable font (`res/font/inter.ttf`, SIL Open Font License, see `docs/licenses/Inter-OFL.txt`). **IBM Plex Mono** only for raw SMS excerpts.
- Bundled, never downloaded at runtime, so it renders identically offline.
- Apple's own typeface (SF Pro) is licensed for Apple devices only and must **not** be shipped.

**Weights:** Regular (400), Medium (500), SemiBold (600), Bold (700).

**Type scale (sp):**

| Role | Size | Weight | Use |
|---|---|---|---|
| Hero number | 48 | Bold | "₹160 left today" amount, one per screen max |
| Large title | 34 | Bold | The title at the top of a tab: Today, History, Settings |
| Title | 22 | Bold | Sub-screen titles, sheet and onboarding titles, card amounts |
| Headline | 17 | SemiBold | Row titles that need weight, filled-button labels |
| Body | 17 | Regular | Row primary text, amounts in rows |
| Subhead | 15 | Regular | Secondary lines, chips, segmented controls |
| Footnote | 13 | Regular | Row captions, section headers (in capitals) |
| Caption | 12 | Regular | SMS excerpts |
| Tab label | 11 | Medium | Bottom bar labels |

**Money formatting**
- Always **tabular (fixed-width) numerals** for any amount, so columns align.
- Indian grouping: ₹1,00,000, not ₹100,000.
- Always the ₹ symbol, no space: ₹1,299. Never "Rs", "INR" or "1299.0".
- Paise only when non-zero: ₹1,299 and ₹1,299.50, never ₹1,299.00.
- Amounts right-aligned in lists.
- Debits shown plain; credits prefixed with "+" in the credit colour. Never use a minus sign for debits.

---

## 4. Colour

**Material You dynamic colour is OFF.** The app uses its own fixed palette. Both light and dark themes are supported; follow the system setting unless the user picks one in Settings.

### Tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | #F2F2F7 | #000000 | Page background behind sections |
| `surface` | #FFFFFF | #1C1C1E | Grouped sections, cards, sheets |
| `text` | #000000 | #FFFFFF | Primary text |
| `text-secondary` | #6C6C70 | #98989F | Captions, section headers |
| `divider` | #C6C6C8 | #38383A | Hairline separators between rows |
| `fill` | #E5E5EA | #2C2C2E | Secondary buttons, chips, text fields, segmented track, bar track |
| `raised` | #FFFFFF | #636366 | The selected segment of a segmented control |
| `accent` | #007AFF | #0A84FF | Primary action, links, selected tab |
| `on-accent` | #FFFFFF | #FFFFFF | Text on accent fills |
| `over` | #D70015 | #FF453A | Over limit, destructive rows |
| `near` | #B25000 | #FF9F0A | Near limit (80%+), review badge |
| `credit` | #248A3D | #30D158 | Credit amounts |
| `lent` | #8E8E93 | #8E8E93 | Lent rows, chevrons |
| `on` | #34C759 | #30D158 | Switches that are on |

Light-theme text colours are the darker "accessible" variants so they stay above 4.5:1.

### Colour rules
- **Colour means state, never decoration.** If something is coloured, the user should be able to say why.
- **Accent** is used for the primary action on a screen, tappable text, and the selected tab.
- The **hero number** is `text` normally, `near` at 80%+, `over` past the limit.
- **Lent** rows: text and amount in `lent` grey, plus a small "Lent" label. Never rely on colour alone.
- No gradients, glows, coloured shadows or tinted section backgrounds.

---

## 5. Spacing, shape, elevation

- **Spacing scale (dp):** 4, 8, 12, 16, 24, 32. No other values.
- **Screen side margin:** 16dp. Content inside a section is inset a further 16dp.
- **List row height:** 56dp minimum (single line + caption).
- **Corner radius:** sections and cards 12dp; buttons and text fields 12dp; sheets and dialogs 20dp; segmented control 12dp with an 8dp selected segment; chips, badges and the add button fully rounded.
- **Separators:** 0.5dp hairline in `divider`, inset 16dp on the leading side, never under the last row of a section.
- **Elevation:** none. Sections separate from the page by `surface` against `bg`, never by drop shadows.
- **Touch targets:** 48dp minimum.

---

## 6. Components

### Grouped sections (the default container)
- Every list (home recent, history days, owed to you, settings, accounts) is an **inset grouped section**: a `surface` block inset 16dp from the screen edges with 12dp outer corners, rows separated by hairlines.
- A section may have a **header**: Footnote, capitals, `text-secondary`, aligned with the section's content.
- Row layout: left = title (Body) with caption below (Footnote); right = amount (Body, tabular, right-aligned), a switch, or a chevron.
- Rows that open another screen end in a small grey **chevron**. Rows that toggle something end in a **switch**; the whole row toggles.
- Tap a transaction row to open edit. No chevron on transaction rows.

### Decision cards
- Items that need a user decision (To review) are separate `surface` cards, 12dp radius, no border.
- Every decision card shows:
  1. What kind of decision (Footnote, `text-secondary`), e.g. "Which account?"
  2. The transaction: amount + payee + time
  3. **The SMS excerpt** that triggered it, in Plex Mono Caption, one or two lines, with the key part visible
  4. The actions

### Buttons
- **Primary:** filled `accent`, 12dp radius, 48dp tall, Headline label. **At most one per card or screen.**
- **Secondary:** filled `fill` with `accent` label, same shape.
- **Text button:** plain `accent` label.
- **Dismissive** ("Neither is mine", "Not a transaction", "Skip"): plain label in `text-secondary`.
- Stacked full-width buttons are only used when the options are equal choices (e.g. picking between two accounts); otherwise side by side.
- The one floating **add button** on Home is a round `accent` circle with a "+".

### Segmented control
- For one-of-a-few choices (Spent | Received, Mine | Not mine, System | Light | Dark): a `fill` track with the selected segment in `raised` and SemiBold text. 48dp tall overall.

### Switches
- On = `on` green track with a white thumb; off = `fill` track with a white thumb, no border.

### Chips
- Capsule shape, `fill` background, no border, Subhead text. Selected = filled `accent`.
- Chips **wrap onto multiple lines**. Never a horizontally scrolling row that hides options off-screen.
- "Lent" and "Self-transfer" chips are visually separated from spending categories (after a small gap), because they change how money is counted.

### Text fields
- A `fill` block with 12dp radius and no border; the cursor is `accent`.

### Progress (limit)
- 8dp bar with rounded ends, full width, `fill` track, fill in `text` / `near` / `over` by state.
- No circular rings or gauges.

### Icons
- Material Symbols Outlined, 20dp in rows, 24dp in the bottom bar. The back button is a thin left chevron in `accent`.
- Icons only where they carry meaning. No decorative icons.

### Motion
- 150–200ms, standard easing. No bounce, spring or playful animations.
- Resolved To-review cards slide out; the count updates.
- Undo is offered via a snackbar for 5 seconds after any decision or edit.

---

## 7. Screens

### Home
Order, top to bottom:
1. **Date and title:** the date in small capitals, then the large title "Today".
2. **Hero section:** "₹X left today", below it "₹Y of ₹Z spent", then the limit bar. Over limit: "₹X over limit" in `over`.
3. **Summary section:** "₹N spent in October" (opens History), then "To review" with a count badge when N > 0, then "Automatic capture is off · Turn on" when it applies.
4. **Recent:** a section of the last ~10 rows, ending in a "See all" row in `accent`.

No other widgets on home.

### To review
- Title: "To review" with the count.
- A vertical list of decision cards (section 6).
- Similar items are grouped with a bulk action, e.g. "6 payments to SWIGGY · Tag all as Food".
- Empty: "Nothing to review." and nothing else.

### History
- Large title, then a search field (payee or amount).
- Filter chips below: date range, account, category, type. Filters wrap; active filters are filled `accent` with an ×.
- One section per day, with a header: the day in capitals on the left, the day's total spent on the right.

### Owed to you
- Header line: "₹X owed by N people" (Body).
- One section of people: name, "since [date]" caption, amount right.
- Tap a row to see that person's lent entries and repayments, with two actions: **Mark repaid** and **Write off**.

### Couldn't read
- Shown as cards in To review: sender, time, SMS excerpt (Plex Mono).
- Tap: a sheet where the user taps the amount in the message, then picks debit / credit / not a transaction, then "Just this one" or "All like this".
- Learned templates are viewable and undoable in Settings.

### First run
1. **Permission explanation screen** before the system SMS prompt: plain explanation of what's read, that nothing leaves the phone, and that manual mode works without it. (Also required by Play Store prominent-disclosure rules.)
2. **Reading:** a progress bar while messages are read and, after confirmation, while past transactions are added.
3. **Account confirmation:** the accounts and wallets that appear in 10 or more messages, each with how often it was seen and a "Mine | Not mine" segmented control. One "Done" primary button.
4. **Set daily limit:** one numeric field, one button.

### Manual entry
- A sheet with Spent | Received, the amount, an optional "Where?" field, category chips and a number pad.
- Defaults: today's date, spent. Save is one primary button.

### Settings
- Large title, then sections: Spending (daily limit, categories with Daily / Not daily, owed to you), SMS capture (read bank SMS switch, accounts, learned messages), Notifications (two switches), Appearance (theme, widget), Your data (storage note, privacy policy, last crash report when there is one, delete all data).

### Widget
- `surface` background with 24dp corners, the amount in Bold, an 8dp bar, and a round `accent` "+" button in the taller size. Fixed palette, never the wallpaper's.

---

## 8. Copy

- **Plain and short.** "5 to review", not "5 to go · one tap each".
- **Say what happened, with numbers.** "₹1,000 out of CUB ••1234 and ₹995 into Amazon Pay, 2 min apart."
- No exclamation marks, emoji, jokes or cheerleading ("Great job!", "You're on fire").
- No financial advice or judgement ("You're spending too much on food").
- Account masks always written as "••1234".
- Dates: "Today", "Yesterday", then "12 Sep". Year only when not the current year.
- Section headers are written in sentence case in code and shown in capitals.

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

- SMS permission denied: a single row on home, "Automatic capture is off · Turn on", tapping to the permission explanation screen.
- Notifications denied: a caption on the switch rows in Settings only; never nag on home.

---

## 10. Accessibility

- Text contrast at least 4.5:1 against its background in both themes.
- The UI must work at 200% system font size without clipping amounts (amounts wrap below the payee if needed).
- Never use colour as the only signal (lent has a label, over-limit has the word "over").
- Every icon-only button has a content description. Switch rows and segmented controls announce their state.

---

## 11. Never do

- Sample, seed or demo data in the shipped app
- Material You dynamic colour
- Shipping Apple's SF fonts, icons or other Apple assets
- Drop shadows, gradients, glows, glassmorphism
- Full-width rows on the bare page background (rows always sit in a section)
- Horizontally scrolling chip rows that hide options
- More than one primary (filled accent) button per card or screen
- Circular progress rings for the limit
- Decorative illustrations, emoji, celebratory animations
- Amounts without tabular numerals, or formatted as "Rs" / "1299.0"
- A decision card without the SMS excerpt that triggered it
