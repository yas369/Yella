# Product Context: SMS Ledger

Why this product exists, what it is, and the thinking behind its shape. Read this first; the detail lives in the spec (`sms-ledger-v0-features.md`), [`tasks/plan.md`](tasks/plan.md), [`docs/flows.md`](docs/flows.md) and [`docs/design-system.md`](docs/design-system.md).

---

## The idea in one line

**An Android app that turns the bank SMS already on your phone into a spending ledger, and tells you, at a glance, how much you can still spend today.**

---

## Why

### The problem

In India, almost every payment (UPI, card, wallet, bank transfer) already sends an SMS. The record of your spending is sitting in your inbox. But it's useless as it is:

- **It's scattered.** One payment can produce two or three messages: the bank's debit, the merchant's receipt, the wallet's notice. Some are duplicates, some are labels, and some are the only record.
- **It's noisy.** OTPs, mandates, "will be debited" reminders, failed attempts and promotions sit next to real payments.
- **It answers nothing.** Nothing in the inbox tells you "you've spent ₹460 today and have ₹40 left", which is the question that actually changes behaviour.

### Why existing approaches fall short

| Approach | What goes wrong |
|---|---|
| Manual expense trackers | People stop logging within days. The ledger is only as good as their discipline. |
| Bank apps | One bank each. Most people have two or three accounts plus a wallet, so no single app sees the whole picture. |
| Apps that sync data to a server | You hand your full financial message history to a company. Many people, reasonably, won't. |
| Budgeting by month | A monthly budget is too far away to guide today's decision. You find out you overspent on the 25th. |

### The bet

1. **The data is already there.** If the app reads the SMS for you, the ledger fills itself, so there's no logging habit to keep up.
2. **Daily beats monthly.** One number, "₹X left today", is easier to act on than a monthly budget spread across categories.
3. **On-device earns trust.** If nothing ever leaves the phone, people can give an app their bank messages without worrying about it.
4. **Honesty beats cleverness.** A ledger that is visibly wrong once is never trusted again. So the app never silently drops or guesses; anything uncertain goes to the user, once.

---

## What

### The product

- **Reads bank and wallet SMS on the phone** and turns them into transactions: amount, direction, account, payee and UPI reference.
- **Discovers your banks from your inbox.** There is no hardcoded bank list. A sender counts as a bank only when its messages carry masked account digits together with an amount.
- **Asks once which accounts are yours.** A family member's account on the same phone number can be ignored permanently.
- **Prevents double counting:**
  - Merchant and payment-app messages only *label* a bank payment; they never add money.
  - Transfers between your own accounts are paired and don't count.
  - Two genuine payments of the same amount are never merged.
- **Shows "₹X left today"** against a daily limit you set, or "₹X over limit", never a negative number.
- **Lets you correct anything with one tap:** category, lent, self-transfer, not a transaction, amount, payee. Every correction can be undone.
- **Learns new message formats from you.** When a bank changes its wording, the message lands in "Couldn't read". You teach it once, and future messages in that format are read automatically.
- **Tracks money you've lent:** who owes you, partial repayments, and write-offs.
- **Works without SMS access too.** Manual mode has every feature except automatic capture.

### What it is not (v0)

- Not a budgeting suite: no per-category or monthly limits.
- Not a cloud service: no accounts, no sync, no server, no internet permission.
- Not an investment or credit product.
- Not "AI": no ML classifier and no automatic categorisation in v0. Categories are set by the user, once per payee.

### Who it's for

People in India who pay mostly digitally, across more than one bank account or wallet, and who want to keep day-to-day spending in check without logging every expense, and without sending their financial data anywhere.

*(This is the intended audience from the spec's design, not validated user research.)*

---

## How it should feel

| Principle | What it means in the product |
|---|---|
| **The answer with zero taps** | The app opens on "₹340 left today". The home-screen widget shows the same number without opening the app at all. |
| **No confirmation for known cases** | A known message format from a known payee goes straight into the ledger. |
| **One queue for everything uncertain** | Unread messages, ambiguous accounts, possible transfers and new payees all go to "Needs you". |
| **One tap per decision** | Big buttons, never forms. "Tag all 6 Swiggy payments" is a single tap. |
| **Never silent, never negative** | Nothing is dropped without being shown. Overspending reads "₹120 over limit". |
| **Native Android** | Material 3 Expressive, wallpaper colours, Android navigation patterns, widget, launcher shortcut. It should feel like it came with the phone. |

---

## Constraints that shape it

- **Google Play SMS policy.** `READ_SMS` and `RECEIVE_SMS` are restricted permissions. The app must qualify under the *SMS-based money management* exception, make SMS clearly its core function, show a clear disclosure before asking, and publish an accurate privacy policy and Data Safety form. Google may reject. Manual mode is the fallback.
- **Bank SMS formats are not a stable API.** Banks change wording without warning, so the design treats parsing failures as normal and visible: "Couldn't read", template learning, and an undo for every learned format.
- **Phone makers kill background apps.** Live capture and alerts can be delayed, so the app catches up on every open and asks to be exempted from battery optimisation.
- **Privacy is a feature, not a setting.** The app has no INTERNET permission and no backups, never logs message bodies, and stores only messages from confirmed senders.

---

## What success looks like

These are proposed measures for v0; they have not been agreed yet.

- **Accuracy:** at least 95% of completed-transaction messages from confirmed senders are read without help (measured on the ~400 MB test export).
- **No double counting:** a sample month reconciles against the bank statement.
- **Low effort:** after the first-run setup, most days need no input beyond glancing at the number.
- **Trust:** the app gets approved under Play's SMS policy, with nothing leaving the device.

---

## Where it stands (24 Oct 2026)

- **Built:**
  - the plan, the flows and the design system;
  - a full Material 3 Expressive UI with the widget and launcher shortcut;
  - the core ledger rules (daily and monthly totals, category rules, lent book), with tests.
- **Runs on sample data.** The app doesn't yet save anything or read real SMS.
- **Next:** saving to the database (T6), then the SMS pipeline. Reading the test export (T2) and the parser (T3–T5) need the ~400 MB file.
- **Biggest risks:**
  - Play's SMS permission review;
  - parser accuracy across real bank formats.
