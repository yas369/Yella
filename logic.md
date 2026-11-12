# SMS Ledger — Application logic

Every rule and algorithm the app runs, as decided in the design discussion. This is the reference for whoever writes the code.

- **What** the app does: `sms-ledger-v0-features.md`
- **How it looks**: `design.md`
- **How it decides** (this file): data model, pipeline, and every algorithm
- **Comparison with another app's approach**: `claude/logic_2.md`

**Framework-neutral.** Algorithms are written as pseudocode so they apply whether the UI is built in Kotlin/Compose, Flutter or React Native. The SMS receiver, inbox reading and background jobs (sections 3 and 20) are Android-native in every case; in Flutter or React Native they live in a native plugin that hands messages to this logic.

**Status tags:** **[Decided]** agreed in discussion · **[Default]** chosen default, may be tuned · **[Assumption]** filled a gap the discussion didn't cover; confirm before building.

Message formats shown in this file are illustrative shapes with placeholders, not real data.

---

## Contents

1. Principles
2. Data model
3. Ingestion
4. Sender normalisation
5. Message pre-filter
6. Field extraction
7. Sender hints (a sender name is never proof)
8. Accounts: the account gate, identifiers, discovery, merging, confirmation, matching
9. Template memory (learning formats through groups)
10. Message processing decision tree
11. Label matching and duplicate alerts
12. Self-transfer pairing
13. Manual entry ↔ SMS merge
14. Payees and categories
15. Lent and repayments
16. Totals, the daily limit and balances
17. Notifications
18. Review queue (grouped questions)
19. Edits and undo
20. Background work and scheduling
21. Modes and permissions
22. Privacy and storage
23. Validating against the dataset
24. Assumptions to confirm
25. Revision log

---

## 1. Principles [Decided]

These override any individual algorithm below.

1. **Only messages with account evidence add money to the ledger.** Account evidence is account digits that match one of the user's accounts, or a confirmed wallet sender. A sender name on its own is never evidence. Everything else is label-only, ignored, or asked about.
2. **Two bank transactions are never merged.** (Section 11 covers the one exception: the *same* alert delivered twice.)
3. **Nothing is dropped silently.** A message either becomes a transaction, is explicitly ignored for a stated reason, or lands in the review queue.
4. **Uncertain → ask the user; certain → no question.**
5. **Every automatic decision is editable and every edit is undoable.**
6. **No sample data.** The ledger starts empty and is filled only from the user's SMS and manual entries.
7. **Nothing leaves the device.** No network calls in the logic layer.
8. **One question, one group.** Whenever the app asks the user something, it first gathers every other pending item that would get the same question and asks once for the whole group. The user can still change any single item in the group (section 18).
9. **The account number outranks the bank name.** An account is identified by its digits. The sender name is a hint that helps, never the deciding fact. A message from a "bank" sender that carries no account evidence is not trusted just because of who sent it (section 8.0).

---

## 2. Data model

Field types are logical, not storage-specific. Every record has an `id` and `created_at`.

### RawSms
The original message, stored only when it may matter (section 22).
| Field | Notes |
|---|---|
| `sender_raw` | As received, e.g. `XX-HEADER-S` |
| `sender_key` | Normalised brand (section 4) |
| `body` | Full text (multipart messages joined) |
| `received_at` | SMS timestamp from the system, in ms |
| `content_hash` | hash(sender_key + received_at + body); unique, makes ingestion idempotent |
| `status` | `transaction` · `label` · `ignored` · `unreadable` · `pending` |
| `ignore_reason` | `otp` · `not_completed` · `balance_only` · `promo` · `no_account` · `not_mine` · `user_marked` · `user_deleted` · `duplicate` |

### Sender
| Field | Notes |
|---|---|
| `sender_key` | Primary key |
| `kind` | `bank` · `wallet` · `label_only` · `unknown`. A **hint** only (section 7); it never makes a message a transaction |
| `user_confirmed` | Whether the user confirmed the kind |
| `stats` | Message count, count with digits+amount, first/last seen |

### Account
One real-world account. It can be seen under several sender names and several digit lengths, so it holds a **list of identifiers** instead of one sender and one set of digits.
| Field | Notes |
|---|---|
| `identifiers` | List of `(sender_key, digits)`. `digits` is the cleaned run (section 8.1), empty for wallets. Any message matching one identifier belongs to this account |
| `kind` | `bank_account` · `card` · `wallet` |
| `ownership` | `mine` · `not_mine` · `unconfirmed` |
| `display_name` | e.g. `{bank} ••{last 4 digits}` or `{wallet}`; uses the longest known digits |
| `balance` | Last balance read from an SMS, in paise; null if none seen yet (section 6.7) |
| `balance_as_of` | `received_at` of the SMS the balance came from |

### AccountPairAnswer
Remembers a user's answer about two possibly-identical accounts so the app never asks twice.
`account_a`, `account_b` (or identifier pairs), `answer` (`same` · `different`).

### Transaction
| Field | Notes |
|---|---|
| `account_id` | |
| `amount` | Integer **paise** (never floating point) |
| `direction` | `debit` · `credit` |
| `type` | `debit` · `credit` · `self_transfer` · `lent` · `repayment` |
| `occurred_at` | SMS `received_at`, or user-chosen time for manual |
| `source` | `sms` · `manual` · `merged` (manual + SMS) |
| `raw_sms_id` | Null for manual |
| `upi_ref` | Nullable |
| `payee_id` | Nullable |
| `category_id` | Nullable (= uncategorized) |
| `note` | User text |
| `label` | Merchant label attached from a label-only SMS |
| `link_id` | Self-transfer pair or lent link |
| `deleted` | Soft delete, for undo. A deleted transaction is never re-created (section 19) |

### Template (learned)
| Field | Notes |
|---|---|
| `sender_key` | Templates are per sender |
| `shape` | The masked text of the sample message the user taught from (section 9.1). New messages are compared to it loosely (section 9.2) |
| `answer` | `debit` · `credit` · `not_transaction` |
| `amount_slot` | Which `{AMT}` occurrence is the amount, counted from the start of the message (index). Empty for `not_transaction` |
| `active` | False after undo |

### Payee
| Field | Notes |
|---|---|
| `payee_key` | Normalised VPA, phone, or merchant name (section 14) |
| `display_name` | |
| `default_category_id` | Sticky category, nullable |

### Category
| Field | Notes |
|---|---|
| `name` | User-defined |
| `not_daily` | Excluded from the daily limit when true |
| `use_count` | Drives chip ordering |

### LentEntry
| Field | Notes |
|---|---|
| `transaction_id` | The debit marked lent |
| `payee_id` | Who owes |
| `outstanding` | Paise, decreases with repayments |
| `status` | `open` · `repaid` · `written_off` |

### Repayment
`lent_entry_id`, `transaction_id` (the credit), `amount_applied`.

### ReviewItem
`kind` (section 18), references to the records involved, `group_key` (for grouping), `status` (`open` · `resolved`), `created_at`.

### EditEvent (for undo)
`target`, `before` snapshot, `after` snapshot, `scope` (`one` · `all_like_this`), `created_at`, `undone`.

### Settings
`daily_limit` (paise, **null until the user sets it**), `mode` (`automatic` · `manual`), `last_scan_at`, notification state (section 17).

---

## 3. Ingestion [Decided]

Three entry points, one processing function. Processing is **idempotent**: the same SMS arriving twice through different paths is stored once.

```
function ingest(sms):
    if sms.type != INBOX: return                    # only received messages
    if not looksLikeBusinessSender(sms.sender): return
    sms.body = joinMultipart(sms)
    hash = contentHash(normalise(sms.sender), sms.received_at, sms.body)
    if RawSms.exists(hash): return                  # idempotent
    raw = RawSms.insert(..., status = pending)
    process(raw)                                    # section 10
```

**`looksLikeBusinessSender`** [Assumption]: Indian business SMS come from alphanumeric headers (`XX-HEADER-S`); personal SMS come from phone numbers. A sender that is only digits (optionally with `+91`) is personal → never stored, never read further.

**Entry points**
| Path | When | Notes |
|---|---|---|
| Live receiver | Each new SMS | Real-time path |
| Catch-up scan | Periodically and after reboot | Reads the inbox from `last_scan_at − 3 days` [Default]. The overlap is safe because ingestion is idempotent, and 3 days covers a phone that was off or an app that was killed over a long weekend. Recovers messages missed when the OS killed the app |
| First-run import | Once, after SMS permission | Reads the full inbox in batches (e.g. 500), oldest first, reporting progress |

The live path may be killed by the OS; the catch-up scan is the real guarantee.

---

## 4. Sender normalisation [Decided]

Indian DLT sender headers look like `XX-HEADER-S`: a 2-letter operator/circle prefix, the brand, and an optional 1-letter suffix. Different prefixes for the same brand must collapse to one key.

```
function normaliseSender(raw):
    s = uppercase(trim(raw))
    s = removePrefix(s, /^[A-Z]{2}-/)               # operator/circle prefix
    s = removeSuffix(s, /-[A-Z]$/)                  # S/P/T/G suffix
    return s
```

Brands that send from several headers (e.g. one bank using two header names) stay as separate senders. Sender keys are **not** merged automatically. The same real account seen under two sender keys is joined at the account level (section 8.4) by its digits.

---

## 5. Message pre-filter [Decided]

Run on every business-sender message before extraction. **Order matters**; the first match wins.

| # | Check | Result |
|---|---|---|
| 1 | Contains an OTP marker (`OTP`, `one time password`, `verification code`) | ignored: `otp` |
| 2 | Not a completed transaction: `will be debited/credited/deducted`, `will be auto-debited`, `request`, `requested`, `mandate`, `autopay set up`, `created`, `revoked`, `expired`, `failed`, `declined`, `scheduled`, `insufficient` | ignored: `not_completed` |
| 3 | Not a payment event [Default]: money earmarked rather than moved (`ASBA`, `is blocked in your`); a bill or due reminder (`due on`, `is due`, `overdue`, `minimum amount due`, `in arrears`) **with no completed transaction verb**; an invitation to convert a bill into EMI (`convert` with `EMI` or `SmartEMI`) **with no debit verb**; a reward or gift voucher delivery (`voucher` with `received`, `reward` or `redemption`) **with no spent / debited / charged**; a merchant acknowledgement (`have received payment`) | ignored: `not_completed` |
| 4 | Balance-only: mentions a balance but no completed transaction verb | **balance update** (section 6.7), then ignored: `balance_only`. No transaction is created |
| 5 | Promotional: contains a promo marker and no completed transaction verb | ignored: `promo` |
| 6 | Otherwise | continue to the learned-template check and the account gate (sections 9.4, 8.0) |

**Promo markers** [Default, tune on the dataset]: `offer`, `pre-approved`, `eligible`, `apply now`, `avail`, `upgrade`, `upto` / `up to`, `loan`, `EMI`, `reward points`, `T&C`, `click`, `download`, `lakh`-style shorthand amounts.

**Completed transaction verb.** A verb from section 6.2 counts only in its completed form. It does not count inside a question, request or instruction ("Pay ₹… now", "Have you paid…?", "will be debited").

Rules:
- **Links do not cause rejection.** Some genuine bank alerts include a link. A link is only a weak signal; it doesn't change the result on its own.
- **"Cashback" does not cause rejection.** A credited cashback is a real credit.
- A word like "offer" never rejects a message that has a completed transaction verb; row 5 applies only when there is none. Rows 2 and 3 are the only places a keyword rejects on its own, and each is a wording that describes something that has not happened or does not move money.
- All keyword matching is case-insensitive, on word boundaries.
- Passing the pre-filter is not enough to become a transaction. The account gate (8.0) still decides.

Row 3 lists wordings that real bank senders use for messages that carry an amount but move no money. Treat it as a starting set and extend it from the `not_sure` and `no_account` groups in the dataset report (section 23).

Ignored messages are kept (status + reason) so the counts are auditable (section 23).

---

## 6. Field extraction [Decided]

Regex is the first-line extractor. Patterns below are the starting set and must be tuned against the real dataset (section 23).

### 6.1 Amount

```
AMOUNT = /(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)/i
SHORTHAND = /(?:rs\.?|inr|₹)\s*[0-9.,]+\s?(?:k|l|lac|lakh|lakhs|cr|crore)\b/i
LIMIT_CLAUSE = /\b(?:avl|avail|available)\.?\s+(?:credit\s+)?(?:limit|lmt)\b\s*(?:is)?\s*:?\s*(?:rs\.?|inr|₹)?\s*[0-9][0-9,]*(?:\.[0-9]+)?/i
EXCLUDE_CONTEXT = /(avl|available|bal|balance|limit|lmt|due|outstanding|min|upto|up to|offer|loan|emi|eligible|worth)\b/i
```

```
function extractAmount(body):
    body' = remove LIMIT_CLAUSE matches            # the whole "Avl Limit: INR 57,888.30" clause, figure included
    body' = remove SHORTHAND matches from body'    # "Rs 2L limit" is never an amount
    candidates = all AMOUNT matches in body'
    for each c: c.excluded = EXCLUDE_CONTEXT occurs within 20 chars before c
    eligible = candidates where not excluded
    if eligible is empty: return none
    verb = first transaction verb (6.2)
    return eligible match closest to verb           # ties → earlier one
```

- Credit-card alerts print the available limit in the same style as the amount. Removing the whole limit clause (figure included) first is the main defence; `EXCLUDE_CONTEXT` stays as the second one for limit wordings the clause pattern does not know.
- Convert to **integer paise**: strip commas, multiply by 100, round. Never floats.
- Amount ≤ 0 → no amount.

### 6.2 Direction

```
DEBIT_VERBS  = debited, withdrawn, spent, purchase, purchased, paid, sent, transferred to, payment of, txn of
CREDIT_VERBS = credited, received, deposited, refund, refunded, reversed, cashback
```

```
function extractDirection(body):
    v = earliest completed verb match from either list   # completed form only (section 5)
    if none: return none
    return debit if v in DEBIT_VERBS else credit
```

Earliest match wins, because a debit alert often ends with "… credited to {payee}", which describes the other party.

### 6.3 Account digits

```
ACCOUNT = /(?:a\/c|acct|account|card|ac)\s*(?:no\.?)?\s*[:\-]?\s*(?:ending|ending with)?\s*[x*•]*([0-9]{3,12})\b/i
```
The captured run may be 3 to 12 digits (some banks print a long run with leading zeros, e.g. `0000` followed by the last digits, or only 3 digits after several `x`). Pass the run to `cleanDigits` (section 8.1); never cut it to a fixed length at this step. If the keyword that matched is `card`, the account is `kind = card`.

**Reject a run that is really a date.** [Default] After a match, check the surrounding text; if the captured digits are part of a date, the match is discarded and the next one is tried.

```
function digitsAreADate(run, body):
    # the run sits inside dd/mm/yyyy, dd-mm-yyyy, dd/mm/yy, dd-mm-yy, yyyy/mm/dd or yyyy-mm-dd,
    # including after "on" or "dated"
    return true if any such date pattern in body contains the run's last digits in its year position
```
The pattern requires an account keyword directly before the digits, so a bare year is accepted only when it follows one (`A/c 2025` is a real account mask). The check is for messages such as "…debited from A/c on 04/11/2025" where a loose match can pick up the year.

### 6.4 UPI reference

```
UPI_REF = /(?:upi\s*ref(?:erence)?|ref(?:erence)?\s*(?:no|id|number)?|rrn|txn\s*id)\s*[:.\-]?\s*([0-9]{12})\b/i
```
UPI references are 12 digits. Store the full 12; never truncate (it's the dedup key, within the time window in section 11.2).

### 6.5 Payee / payer

In order of preference:
1. **VPA:** `/[a-z0-9._\-]{2,}@[a-z]{2,}/i`
2. **Phone:** 10 digits starting 6–9, optionally prefixed `+91`
3. **Merchant name:** text after `to` / `at` / `from` / `by`, up to the next `on`, `ref`, `via`, `.`, `(` or end

### 6.6 Timestamp

`occurred_at` = the SMS's system `received_at`. Dates inside the body are ignored in v0 [Default]: body dates vary wildly in format, and the SMS time is within minutes of the transaction.

### 6.7 Available balance [Decided]

Many transaction alerts, and some balance-enquiry replies, state the balance after the transaction.

```
BALANCE = /(?:avl\.?\s*bal(?:ance)?|available\s*bal(?:ance)?|bal(?:ance)?)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)/i
```

```
function updateBalance(raw, account):
    b = first BALANCE match in raw.body, in paise
    if b is none: return
    if account.kind == card: return                 # card "balance" is not spendable money
    if account.balance_as_of is none or raw.received_at > account.balance_as_of:
        account.balance = b; account.balance_as_of = raw.received_at
```

- The balance is **the last one the bank told us**, not a live figure. It is always shown with its `balance_as_of` time.
- Cash the user spends without an SMS (manual entries) does not change it.
- Only the newest message wins, so a late catch-up scan of old messages cannot overwrite a newer balance.
- Available-limit and outstanding amounts are never balances (they are excluded in 6.1 and not matched here).

---

## 7. Sender hints [Decided]

**A sender name is never proof that a message is a transaction.** A bank's header also sends offers, loan pitches, reminders and fraud warnings. Classifying the whole sender as "bank" and trusting everything it sends was the cause of spam being counted. The sender kind is now only a hint with three limited jobs: it speeds up work, it identifies wallets, and it keeps label-only senders from ever creating money.

```
function classifySender(sender):
    msgs = messages from sender that passed the pre-filter (section 5)
    withDigitsAndAmount = count(m in msgs where extractAccountDigits(m) and extractAmount(m))
    withAmountAndVerb   = count(m in msgs where extractAmount(m) and extractDirection(m))
    mentionsWallet      = count(m in msgs where m.body matches /wallet|balance in your .* wallet/i)

    if withDigitsAndAmount >= 2:                    return bank
    if mentionsWallet >= 2 and withAmountAndVerb >= 2: return wallet
    if withAmountAndVerb >= 1:                      return label_only
    return unknown
```

What each kind does:
| Kind | Effect |
|---|---|
| `bank` | Helps the first-run screen group discovered accounts under a name. **Gives no trust to any individual message.** |
| `wallet` | The only case where the sender name is evidence, because a wallet has no account digits. It still needs the user to confirm the wallet account as **Mine** (8.3), and the message must be transaction-shaped (8.0). |
| `label_only` | Messages only attach labels (section 11). They never create money. |
| `unknown` | The message still goes through the account gate; a sender with no history gets no shortcut and no penalty. |

- Thresholds of 2 avoid classifying from a single odd message. User confirmation (section 8) overrides classification permanently.
- **Wallet** [Assumption]: wallets are detected by wallet wording and confirmed by the user. If detection proves weak on the dataset, fall back to a small user-editable list of wallet senders.

---

## 8. Accounts [Decided]

The same real account often shows up in different forms: `xxxx123` in one message, `00008123` in another, sometimes under a second sender name. Treating each form as a new account splits one account into several. An account therefore holds a list of identifiers (section 2), and the app decides what to merge by a few clear rules, asking the user only when it cannot be sure.

### 8.0 The account gate [Decided]

Every message that passes the pre-filter must show **account evidence** before it can become a transaction. This is what keeps promotional and other non-transaction messages from a real bank sender out of the ledger.

A message is **transaction-shaped** when it has a completed transaction verb (5, 6.2), an eligible amount (6.1), and no unresolved promo marker without a verb.

```
function accountEvidence(raw, sender):
    # returns ACCOUNT(a) | NEW(d) | AMBIGUOUS | NOT_SURE | NONE
    shaped = isTransactionShaped(raw)

    if sender is a confirmed wallet account:
        return shaped ? ACCOUNT(wallet) : NONE

    d = cleanDigits(extractAccountDigits(raw))

    if d is none:
        if not shaped: return NONE                             # promo, reminder, warning
        mine = accounts with an identifier at this sender and ownership = mine
        if mine has exactly 1 and hasStructure(raw): return ACCOUNT(mine[0])
                                                              # structure = UPI ref, or a balance phrase
        return NOT_SURE                                        # looks like a transaction, no account named

    fits = accounts with an identifier (s, x) where compatible(d, x) and not answered "different" for this pair
           and ( s == sender and shorter side >= 3 digits
              or s != sender and shorter side >= 4 digits )    # digits decide; sender name is secondary
    if fits has exactly 1:
        learn (sender, d) as an identifier of that account
        if the sender differs from the account's known senders and no AccountPairAnswer exists:
            addReview(same_account)        # counted now, confirmed once (8.2)
        return ACCOUNT(that account)
    if fits is empty:
        return shaped ? NEW(d) : NONE                           # digits in a non-transaction create no account
    return AMBIGUOUS
```

Consequences:
- A promo from a bank sender that mentions no matching account and has no completed transaction verb is `NONE` → ignored with reason `no_account`.
- A message that mentions the user's account digits is trusted as that account even if the sender name is new; the digits are the evidence.
- A message that looks like a transaction but names no account goes to the review queue as `not_sure`, grouped by shape (section 18). One answer ("Not a transaction") clears a whole spam template and teaches the app to ignore it next time (section 9).
- Account digits found in a message that is not transaction-shaped never create a new account.

### 8.1 Cleaning digits and comparing

```
function cleanDigits(run):
    d = run with all non-digit characters removed       # drops x, *, •, spaces
    d = removeLeadingZeros(d)                           # "00008123" -> "8123"
    if length(d) < 3: return none                       # too short to identify
    return d

function compatible(a, b):                              # one ends with the other
    return a.endsWith(b) or b.endsWith(a)               # "8123" ~ "123"  (true)
                                                         # "8123" ~ "4123" (false)
```

`display_name` always uses the longest known digits, shown as the last 4.

### 8.2 Discovery

Accounts are discovered from **transaction-shaped messages only** (8.0), so digits in promotional text never create an account.

```
for each transaction-shaped message with digits:
    d = cleanDigits(extractAccountDigits(m)); candidates.add((sender, d))
for each confirmed wallet sender: candidates.add((sender, none))

group candidates into accounts:
    same sender, compatible digits, shorter side has 4+ digits  → one account (silent)
    same sender, compatible digits, shorter side has 3 digits   → one account only if exactly
                                                                 one account at that sender fits;
                                                                 otherwise separate + same_account review
    different sender, compatible digits, shorter side 4+ digits → counted as one account for now,
                                                                 plus one same_account review to confirm
    different sender, compatible digits, shorter side 3 digits  → separate accounts + same_account review
    not compatible                                              → separate accounts
```

- **Why digits first:** the account number is the identity. A chance match of 4 digits between two different accounts is about 1 in 10,000, so counting first and confirming once is better than holding the messages out of the totals.
- **Why still confirm across senders:** the same last digits at two different banks can happen. The user answers once per pair (`AccountPairAnswer`) and the app never asks again.
- If the user answers `different`, the tentative merge is undone (8.4 split) and the messages are re-assigned.

### 8.3 Confirmation (first run)
Each discovered account is shown once with its message count and date range. The user marks it **Mine** or **Not mine**. Answers are permanent until changed in Settings.

- Suspected duplicates are shown as one question per pair, **"Same account?"**, before the Mine / Not mine step (section 8.4, 18).
- `not_mine` accounts: their messages are stored with status `ignored: not_mine`, never counted, never asked about again.
- A **new** account discovered later (new digits at a known bank, or a new wallet) creates a "New account" review item. Its transactions are held as `pending` until answered.

### 8.3.1 Only repeated accounts are asked about [Decided 2026-11-12]

Raw messages name many account numbers that are not the user's: the other party in a transfer, a beneficiary, a one-off card. Asking about each one floods the first-run list.

- An account or wallet is put to the user (first run, and later as "New account") **only once 10 or more transaction-shaped messages name it** (`ACCOUNT_MIN_MESSAGES`).
- Below that, the account's messages are **held, not dropped**: they are counted towards the 10 and read in full if the user later answers Mine.
- If no account in the inbox reaches 10 (a new phone, a cleared inbox), first run lists everything found instead of nothing.
- A message that names two accounts is placed by the one the user confirmed as Mine, wherever it sits in the text.
- Changing an answer in Settings makes the ledger agree: **Not mine** removes that account's transactions and open questions; **Mine** reads its past messages again.

### 8.4 Merging and splitting [Decided]

**Same account → merge**
```
function mergeAccounts(keep, other):
    keep.identifiers += other.identifiers
    repoint every Transaction of `other` to `keep`
    keep.ownership = keep.ownership if confirmed else other.ownership
    keep.balance = newer of (keep.balance, other.balance) by balance_as_of
    store AccountPairAnswer(keep, other, same)
    delete `other`
    record an EditEvent with the full before snapshot, so the merge can be undone
```

**Different accounts → keep both**
Store `AccountPairAnswer(a, b, different)`. The pair is never asked about again.

**Split** (Settings → Accounts, or undo of a merge)
```
function splitAccount(account, identifierToSplit):
    new = Account(identifiers = [identifierToSplit], ownership = account.ownership)
    account.identifiers −= identifierToSplit
    for each Transaction of `account` whose RawSms sender and digits match identifierToSplit:
        move it to `new`
    store AccountPairAnswer(account, new, different)
    record an EditEvent
```
Every transaction keeps a link to its original SMS (`raw_sms_id`), which is how a split knows which transactions came from which identifier. After a split, balances are re-derived from the messages of each side.

After a merge or split, `recompute()` runs (section 16) and self-transfer links that joined an account to itself are reviewed again (section 12).

### 8.5 Matching a message to an account
Matching is done by `accountEvidence` (8.0). In short: wallet by confirmed wallet sender; everything else by digits first, sender second.

---

## 9. Template memory (learning formats through groups) [Decided]

The regex never changes while the app runs. A separate memory learns message formats from the user's answers. It works through the groups of section 18: the user answers once for a group of similar messages, and that one answer then covers every message of the same shape, now and in the future. Two kinds of answer are learned: **how to read a transaction** (debit or credit and the amount position) and **"this is not a transaction"** (for example a repeating promo).

### 9.1 Masking (message → shape)
Deterministic. The variable parts of a message are replaced so that messages of the same format look the same.

```
function toShape(body):
    t = body
    t = replace AMOUNT matches            with "{AMT}"     # keeps the currency word
    t = replace UPI_REF / 12-digit runs    with "{REF}"
    t = replace VPA matches               with "{VPA}"
    t = replace phone numbers             with "{PHONE}"
    t = replace date patterns             with "{DATE}"
    t = replace time patterns             with "{TIME}"
    t = replace remaining digit runs      with "{N}"
    t = replace masked digits (x/*/• + digits) with "{DIGITS}"
    t = replace the payee name span (6.5, merchant name) with "{NAME}"
    t = collapse whitespace; lowercase
    return t                                         # the full masked text
```

### 9.2 Similarity and grouping
Two messages belong to one group when they are close in shape, not only when they are identical. This keeps a different-length merchant name, or an optional extra line such as a balance or a footer, from splitting one format into many groups.

```
function similar(a, b):
    return a.sender_key == b.sender_key
       and wordOverlap(toShape(a.body), toShape(b.body)) >= 0.8        [Assumption]

function wordOverlap(x, y):
    # words in common, counting repeats, divided by the length of the longer word list
```

Grouping is greedy: take messages oldest first; each one joins the first open group whose sample message is similar, otherwise it starts a new group. The sample message of a group is its first member.

### 9.3 Teaching (answering a group)
The user answers once for the group (the card is described in section 18):
1. They tap the number that is the amount in the group's sample → stored as `amount_slot` (the index among `{AMT}` positions, counted from the start). Skipped for "Not a transaction".
2. They choose debit / credit / not a transaction.
3. **Preview before applying.** For every other member of the group the app reads the amount at `amount_slot` from that member's own text and lists it. A member that has fewer `{AMT}` positions than the slot, or whose value is not a valid amount, is shown as "Can't read this one" and moves into its own group.
4. The user can edit any member's value or skip it (section 18.2).
5. They choose the scope:
   - **Just this one:** only the sample message is fixed; no template is saved.
   - **All like this:** save the Template (sender, sample shape, answer, slot) and apply it to every remaining member of the group.

### 9.4 Applying learned templates
Learned templates are checked **before** the account gate and the regex path (the user's teaching beats the generic rules).
```
function learnedTemplate(raw):
    for each active Template t where t.sender_key == raw.sender_key:
        if wordOverlap(toShape(raw.body), t.shape) >= 0.8:
            if t.answer != not_transaction and number of {AMT} positions in raw < t.amount_slot + 1: return none   # never guess
            return t
    return none
```
On a match for a transaction, the amount is read from `amount_slot`; account digits, UPI reference and payee still come from the normal extractors, and the account gate still applies. On a match for `not_transaction`, the message is ignored as `user_marked`. A message that matches no template goes down the regex path (section 10), and if that fails it joins an open similar group, or starts a new one.

### 9.5 Undo
Any learned template can be viewed and turned off in Settings. Turning it off reverts every transaction it produced to `unreadable` and puts them back in review (via an EditEvent, so that is undoable too). Turning off a `not_transaction` template sends its ignored messages back through `process()`.

---

## 10. Message processing decision tree [Decided]

```
function process(raw):
    sender = Sender[raw.sender_key]
    if sender.kind == label_only:     raw.status = label; attachLabel(raw) (section 11); return

    r = prefilter(raw)                                         # section 5
    if r is balance_only:
        ev = accountEvidence(raw, sender)
        if ev is ACCOUNT(a): updateBalance(raw, a)             # section 6.7
        raw.status = ignored(balance_only); return
    if r is ignored:                  raw.status = ignored(r.reason); return

    tpl = learnedTemplate(raw)                                 # section 9.4, before the gate
    if tpl and tpl.answer == not_transaction: raw.status = ignored(user_marked); return

    ev = accountEvidence(raw, sender)                          # section 8.0
    if ev == NONE:      raw.status = ignored(no_account); return
    if ev == NOT_SURE:  raw.status = pending; addReview(not_sure, raw); return

    amount    = tpl ? amountAt(tpl.amount_slot) : extractAmount(raw)
    direction = tpl ? tpl.answer               : extractDirection(raw)
    if amount is none or direction is none:
        raw.status = unreadable; addReview(couldnt_read, raw); return

    if ev == AMBIGUOUS: hold; addReview(which_account, raw); return
    if ev is NEW(d):    hold; addReview(new_account, raw); return    # plus same_account if suggested (8.2)
    account = ev.account
    if account.ownership == not_mine: raw.status = ignored(not_mine); return

    if deletedTransactionExists(raw): raw.status = ignored(user_deleted); return   # 19: never re-create
    if isDuplicateAlert(raw, account, amount, direction): raw.status = ignored(duplicate); return   # 11.2
    if sender.kind == wallet and isWalletUpiFromBank(raw): raw.status = label; attachLabel(raw); return  # 11.3

    tx = createTransaction(raw, account, amount, direction)
    updateBalance(raw, account)     # section 6.7
    tryMergeWithManual(tx)          # section 13
    trySelfTransfer(tx)             # section 12
    tryRepayment(tx)                # section 15
    applyStickyCategory(tx)         # section 14
    attachPendingLabels(tx)         # section 11
    if tx.payee is new: addReview(new_payee, tx)
    recompute()                     # section 16
```

"Held" messages are not counted in totals until their review item is resolved. Every `addReview` call places the item in a group (section 18).

---

## 11. Label matching and duplicate alerts [Decided]

### 11.1 Label-only SMS → bank transaction
A label-only SMS (merchant or payment app) never creates money. It only adds a label (e.g. merchant name) to the bank transaction it describes.

```
function attachLabel(labelSms):
    ref = extractUpiRef(labelSms); amt = extractAmount(labelSms); dir = extractDirection(labelSms)
    if ref:
        tx = transaction with upi_ref == ref
        if tx: tx.label = merchantName(labelSms); return
    if amt and dir:
        cands = transactions where amount == amt and direction == dir
                and |occurred_at − labelSms.received_at| <= 30 min      [Default window]
                and label is empty
        if cands not empty: nearest-in-time.label = merchantName(labelSms); return
    keep labelSms as unmatched (status label); retry when new transactions arrive
```

Bank SMS can arrive after the merchant SMS, so `attachPendingLabels` on each new transaction re-runs this for unmatched label SMS within the window.

A wrong label is low-stakes (no money changes), so no review item is created.

### 11.2 Duplicate alert from the same bank [Default]
Principle 2 says two bank transactions are never merged. A bank occasionally sends the **same alert twice**; that is one transaction, not two.

```
function isDuplicateAlert(raw, account, amount, direction):
    ref = extractUpiRef(raw)
    if ref is none: return false                    # without a ref, never treat as duplicate
    return exists transaction with same account, same upi_ref, same amount, same direction
           and |occurred_at − raw.received_at| <= 24 hours      [Default window]
```

A repeated alert arrives within minutes or hours (carrier delays can stretch it), so 24 hours covers it. Without a window, a later and unrelated payment that happens to carry the same reference would be dropped as a duplicate.

Two ₹20 payments without references always remain two transactions.

### 11.3 Wallet UPI vs wallet balance [Decided]
Wallet senders send two kinds of payment SMS:
- **Paid from wallet balance** → a real wallet debit (transaction).
- **Paid via the wallet's UPI from a bank account** → the bank sends its own debit; the wallet SMS is label-only.

```
function isWalletUpiFromBank(raw):
    return raw mentions a bank account (ACCOUNT pattern matches) or "UPI" with a bank name,
           and does not mention "wallet balance" / "from wallet"
```
[Assumption] Tune these phrases on the dataset.

---

## 12. Self-transfer pairing [Decided]

A self-transfer is a debit from one of the user's accounts and a credit into another. Both stay in the ledger, linked and excluded from spending and income.

```
function trySelfTransfer(tx):
    other_dir = opposite(tx.direction)
    cands = transactions where account != tx.account
            and account.ownership == mine
            and direction == other_dir
            and type in (debit, credit)                       # not already linked
            and |occurred_at − tx.occurred_at| <= 30 min      [Default]
            and feeOk(debitAmount, creditAmount)
    if cands is empty: return
    best = nearest in time
    if both have the same upi_ref and the amounts are equal:
        link(tx, best, self_transfer)                         # certain → automatic
    else:
        addReview(possible_self_transfer, tx, best)           # show fee and time gap
```

```
function feeOk(debit, credit):
    return credit <= debit and (debit − credit) <= min(₹50, 2% of debit)   [Default]
```

- Bank → wallet top-ups follow the same rule (the wallet is an account).
- Linking sets both `type = self_transfer` and a shared `link_id`. Unlinking (user says "Separate") restores the original types.
- A credit can be linked to at most one debit.
- Two accounts that the user has merged as the same account (section 8.4) are one account, so a debit and credit between them are not a self-transfer.

---

## 13. Manual entry ↔ SMS merge [Default]

A user in automatic mode may add a payment manually and then receive its bank SMS.

```
function tryMergeWithManual(tx):              # tx from SMS
    cands = transactions where source == manual and not merged
            and amount == tx.amount and direction == tx.direction
            and |occurred_at − tx.occurred_at| <= 6 hours
    if cands has exactly 1:
        merge(cands[0], tx)                    # automatic
    elif cands has more than 1:
        addReview(possible_duplicate, tx, cands)
```

```
function merge(manual, sms):
    result.account, occurred_at, upi_ref, raw_sms_id ← sms
    result.category, note, payee (if set), type (e.g. lent) ← manual
    result.source = merged
    record EditEvent so the merge can be undone (splits back into two)
```

---

## 14. Payees and categories [Decided]

### 14.1 Payee key
```
function payeeKey(tx):
    if VPA:      return lowercase(vpa)
    if phone:    return last 10 digits
    if merchant: return uppercase(collapse whitespace, strip punctuation)
    return none
```

### 14.2 Sticky category [Default]
- When the user sets a category on a transaction with **All like this**, the payee's `default_category_id` is set.
- New transactions with that payee get that category automatically, with no question.
- **Just this one** changes only that transaction; the payee default is unchanged.
- The payee default changes only through another explicit "All like this".

### 14.3 New payee
A transaction whose payee has no default category creates a "New payee" review item. Until answered, the transaction is **uncategorized** (which counts toward the daily limit; section 16).

### 14.4 Categories
- User-created. `not_daily = true` excludes the category from the daily limit.
- Chip order = descending `use_count`.
- "Lent" and "Self-transfer" are **types**, not categories; they appear beside the category chips but set `type`, not `category_id`.

---

## 15. Lent and repayments [Decided]

### 15.1 Marking lent
The user marks a debit as Lent (one tap). The app never infers it.
```
tx.type = lent
LentEntry.insert(transaction = tx, payee = tx.payee, outstanding = tx.amount, status = open)
```
If the payee has no VPA or phone, the entry is kept but can't be auto-matched; the user marks repayment manually.

### 15.2 Repayment matching
```
function tryRepayment(tx):                        # tx is a credit
    if tx.direction != credit or tx.payee is none: return
    open = LentEntry where payee == tx.payee and status == open
                       and lent transaction occurred before tx
           ordered oldest first                    # FIFO
    if open is empty: return
    remaining = tx.amount
    for e in open while remaining > 0:
        applied = min(remaining, e.outstanding)
        Repayment.insert(e, tx, applied)
        e.outstanding −= applied; remaining −= applied
        if e.outstanding == 0: e.status = repaid
    if remaining == tx.amount: return               # nothing applied
    tx.type = repayment if remaining == 0
    else: split tx → repayment part (applied total) + credit part (remaining)   [Default]
```

A repayment is neither income nor spending.

### 15.3 Manual actions
- **Mark repaid:** closes the entry (`outstanding = 0`, `repaid`) without needing an SMS.
- **Write off:** `status = written_off`; it leaves reminders and the Owed list.
- Both are undoable.

### 15.4 Owed total
`owed_total = sum(outstanding where status == open)`; `owed_people = count distinct payee`.

---

## 16. Totals, the daily limit and balances [Decided]

All totals are computed from transactions, never stored as running numbers, so any edit recomputes correctly. (Balances are the one exception: they are read from SMS, section 6.7.)

### 16.1 What counts as daily spending
```
function countsTowardDaily(tx):
    return tx.type == debit
       and not tx.deleted
       and tx.account.ownership == mine        # manual entries without an account also count
       and not held (pending review of account)
       and (tx.category is none or not tx.category.not_daily)   # uncategorized counts [Default]
```

Never counted: credits, self-transfers, lent, repayments, ignored or held messages.

### 16.2 Day boundary
"Today" = the phone's local calendar day, 00:00–23:59, using `occurred_at` in local time.

### 16.3 Values and the daily limit
```
spent_today = sum(amount where countsTowardDaily and occurred_at is today)
if spent_today <= daily_limit: show "{daily_limit − spent_today} left today"
else:                          show "{spent_today − daily_limit} over limit"
ratio = spent_today / daily_limit               # drives the Today spend ring (design.md)
state = normal if ratio < 80%; near if 80–<100%; over if ≥ 100%
month_total = sum(amount where type == debit and not deleted and account mine
                  and occurred_at in the current calendar month)
```

The month total includes "not daily" categories (it's total spending); there is no monthly target.

**Setting the limit [Decided]**
- The app asks for the daily limit **once, on first launch** (design.md, First run). The user can set it then or choose "Set later".
- The limit can be changed at any time in Settings. A change applies immediately to today's ring and alerts, and from then on. Past days are not re-evaluated, so no per-day history of limits is kept.
- While `daily_limit` is null, Today shows only today's spent amount and a "Set a daily limit" button. No limit alerts are sent.
- Changing the limit resets the "sent today" alert flags only if the new limit moves spending to a lower threshold; a threshold already announced today is not announced again (section 17.1).

### 16.4 Accounts and balances [Decided]
```
accounts_count = count(accounts where ownership == mine and kind == bank_account)
total_balance  = sum(balance where ownership == mine and kind == bank_account and balance is known)
```
- Wallets are listed separately and never counted in `accounts_count`. Cards (`kind = card`) have no spendable balance and are not listed in balances.
- An account with no balance read yet shows "—" and is left out of `total_balance`. If some accounts are missing, the total is shown as "from `{k}` of `{n}` accounts".
- Every balance is shown with its `balance_as_of` time. The balance is the last one a bank SMS reported, not a live balance.
- Balances are sensitive: the app shows them hidden by default and only on the user's explicit tap (design.md, Today). The hidden/shown state is never saved.

---

## 17. Notifications [Decided]

### 17.1 Daily limit alerts [Default thresholds]
```
after every recompute:
    if daily_limit is none: return
    for threshold in (80%, 100%):
        if spent_today >= threshold × limit
           and not sentToday(threshold):
            notify(threshold); markSent(threshold, today)
```
- At most one alert per threshold per day, even if edits drop spending below the threshold and it is crossed again.
- Sent flags reset at local midnight.
- Not sent for transactions dated before today (e.g. found by a catch-up scan of yesterday's messages).
- If notification permission is denied: skip silently; the on-screen state still updates.

### 17.2 Lent reminder
- Scheduled for the **1st of every month** at a fixed morning time [Default: 09:00 local].
- Sent only if `owed_total > 0`: "`{owed_total}` owed to you by `{owed_people}` people".
- Rescheduled after each run and after reboot.

### 17.3 Never
No notification per captured transaction. No engagement or promotional notifications. No balance in any notification.

---

## 18. Review queue (grouped questions) [Decided]

One queue for everything uncertain.

| Kind | Created when | Choices | Effect |
|---|---|---|---|
| `couldnt_read` | The message has account evidence but no readable amount or direction | Tap amount + debit/credit/not a transaction + scope | Section 9.3 |
| `not_sure` | The message looks like a transaction but names no account (8.0) | Not a transaction · It is a transaction (then pick the account) + scope | "Not a transaction": ignored as `user_marked`; "All like this" saves a `not_transaction` template. "It is a transaction": assigns the account, then reads the amount as in 9.3 |
| `same_account` | Two accounts have compatible digits and the app can't be sure they are one (different sender, or only 3 matching digits) | Same account · Different accounts | Same: merge (8.4). Different: keep both, remember the answer, never ask about the pair again |
| `which_account` | 3-digit or missing digits fit more than one account | One of the fitting accounts · None is mine | Assigns account; "none" → `not_mine` for that message |
| `new_account` | Unknown digits in a transaction-shaped message, or a new wallet | Mine · Not mine | Sets ownership; releases held messages |
| `possible_self_transfer` | Pair found but not certain | Self-transfer · Separate | Links or leaves both as-is |
| `possible_duplicate` | Manual entry matches more than one SMS (or vice versa) | Pick the match · Keep both | Merges or not |
| `new_payee` | Payee with no default category | A category chip · Lent · Skip | Sets category (scope prompt) or type |

**Ordering:** items that hold money out of the totals first (`which_account`, `new_account`, `couldnt_read`), then `not_sure`, then `same_account` (it prevents wrong self-transfer links and a split history), then `possible_self_transfer`, `possible_duplicate`, then `new_payee`. Largest group first within a kind, then newest.

### 18.1 Grouping rule [Decided]
Whenever the app asks the user a question, it first collects every other open item that would get the same question and shows them as **one group with one answer**.

Group key by kind:
| Kind | Items are grouped when |
|---|---|
| `new_payee` | Same `payee_key` |
| `couldnt_read`, `not_sure` | Same sender and a similar shape (section 9.2: about 80% of masked words in common, so a different-length name or an optional extra line doesn't split the group) |
| `new_account` | Same account identifier |
| `which_account` | Same sender and the same set of fitting accounts |
| `same_account` | The same pair of accounts |
| `possible_self_transfer`, `possible_duplicate` | Never grouped; each is about one specific pair |

**What a group card shows:** the question, the count (`{n} messages`), one sample, and a way to expand the full list.

**One answer applies to all** by default ("Apply to all `{n}`").

### 18.2 Every item stays individually editable [Decided]
The group never hides errors.
- **Before confirming:** each item in the expanded list shows the value the app will use for it (for example the amount it reads, the account, the category). The user can change that item's value, or take it out of the group with "Skip this one".
- A skipped item becomes its own review item. It is never lost or silently applied.
- **Preview for `couldnt_read` and `not_sure`:** the card lists what the app will do with the other members before the user confirms. If a member looks wrong, the user fixes or skips just that one.
- **After confirming:** every resulting transaction can still be edited on its own (section 19).
- **Undo:** the group answer is one EditEvent with scope `all_like_this`, so one undo reverts the whole group; individual edits afterwards are separate EditEvents and undo separately.

**Skip:** leaves the transaction as-is (uncategorized) and resolves the item. It doesn't reappear for that transaction.

Resolving an item always creates an EditEvent (undoable).

---

## 19. Edits and undo [Decided]

- **Every** automatic decision is editable: amount, direction, type, account, category, payee, "not a transaction", merges, links, learned templates.
- Each edit writes an **EditEvent** with before/after snapshots and its scope.
- **Undo** applies the `before` snapshot. For scope `all_like_this`, undo reverts every record the edit touched and removes the rule it created.
- After any edit or undo, `recompute()` runs (section 16), and the limit notification check runs (17.1).
- The snackbar undo lasts 5 seconds; after that, the same undo is still possible from the transaction's edit sheet.
- Deleting a transaction is a soft delete (`deleted = true`), so it is undoable.
- **A deleted transaction is never re-created** [Decided]. `process()` checks, before creating a transaction, whether a deleted Transaction already exists for the same `raw_sms_id` (or the same `content_hash`). If it does, the message stops as `ignored(user_deleted)`. Every job that re-runs `process()` (rescans, template learned or undone, account merged or split; section 20) obeys the same check, so a delete survives all of them. The only way back is to undo the delete.

---

## 20. Background work and scheduling [Decided]

| Job | Trigger | Does |
|---|---|---|
| Live receive | Each incoming SMS | `ingest(sms)` |
| Catch-up scan | Periodic [Default: every 1 hour] and on boot | Reads inbox since `last_scan_at − 3 days`, ingests each |
| First-run import | After SMS permission granted | Full inbox, batches, progress; then sender hints and account discovery |
| Reclassify | New sender reaches 2 qualifying messages | Section 7; may create `new_account` |
| Reprocess | Template learned or undone; account ownership changed; accounts merged or split | Re-runs `process()` on affected messages; never re-creates a deleted transaction (section 19) |
| Lent reminder | 1st of month | Section 17.2 |
| Midnight reset | Local midnight | Reset limit-alert flags |

- All jobs must be safe to run twice or concurrently; ingestion is idempotent and processing locks per message.
- Battery-optimisation exemption is requested only on brands known to kill background work; the catch-up scan is the fallback either way.

---

## 21. Modes and permissions [Decided]

```
state:
    sms_permission: granted | denied | permanently_denied
    notif_permission: granted | denied
    mode = automatic if sms_permission == granted and user chose automatic, else manual
```

- **Manual mode:** live receive, catch-up and import are off. Everything else (manual entry, categories, lent, limit, history, notifications) works. No balances are available, so the Accounts card on Today shows no balance figures.
- **Switching to automatic** (permission granted later): run the first-run import. Manual entries already present take part in the manual ↔ SMS merge (section 13).
- **Switching to manual:** stop SMS jobs. Existing SMS-derived transactions stay.
- **Permission revoked by the user in system settings:** detected on next launch → switch to manual and show the "Automatic capture is off" row.
- Never re-prompt a denied permission automatically.

---

## 22. Privacy and storage [Decided]

- **Personal SMS** (phone-number senders) are never stored or parsed (section 3).
- **Messages that carry no account evidence** (ignored as `no_account`, `promo`, `otp`, or from senders that never produced a transaction) do not need their bodies kept. [Assumption] Keep the status, reason and sender; delete the body once it has been classified, unless the message is waiting in the review queue.
- Stored bodies (messages behind transactions, labels and review items) stay in the app's private storage.
- **No network access** from the logic layer. No analytics, no crash reporting that sends data.
- **Balances** are stored on the device with the account and are never put in logs, notifications or screenshots of the app switcher. [Assumption] The screen is hidden in the recent-apps view while balances are shown.
- Uninstalling the app removes all data.
- Amounts are integers in paise everywhere.

---

## 23. Validating against the dataset [Decided]

The ~400 MB SMS export is the test dataset. The parser and processing logic must run on a computer against it, outside the phone.

**Invariants that must hold** (checked automatically):
1. Every message ends in exactly one status: `transaction`, `label`, `ignored(reason)`, `unreadable` or held for review. None missing.
2. No UPI reference appears on more than one counted (non-ignored, non-linked) transaction from the same account **within 24 hours** (section 11.2).
3. No label-only SMS creates a transaction.
4. Every self-transfer has exactly two linked transactions.
5. Totals recomputed from scratch equal the incrementally maintained totals.
6. Re-running the import on the same data produces no new records (idempotency).
7. No two accounts at the same sender have compatible identifiers of 4 or more digits, unless the user answered `different` for that pair.
8. No self-transfer links two transactions of the same account.
9. **Every counted transaction has account evidence:** its message carried digits compatible with its account, or came from a confirmed wallet. No transaction exists because of a sender name alone.
10. **No promotional message is counted:** every message with a promo marker and no completed verb is `ignored(promo)`, and no account was created from digits found in a non-transaction-shaped message.
11. Each account's `balance` equals the balance in its newest message that carried one.
12. **No deleted transaction is re-created:** after deleting a sample of transactions and re-running the import and every reprocess job, none of them reappears (section 19).
13. **No account was created from a date:** no account's digits equal the year part of a date found in the message it came from (section 6.3).

**Report to review by hand:**
- Discovered senders by kind, with counts
- Discovered accounts with their identifier lists, message counts and date ranges, and every suspected `same_account` pair
- Counts per status and per ignore reason, **especially `no_account`, `promo` and `not_sure`**; a sample of the `no_account` and `not_sure` groups to check no real transaction is hiding there
- The 20 most common `unreadable` shapes (the next formats to support), and how many groups the `couldnt_read` and `not_sure` messages fall into
- **How many review cards a fresh import produces** (groups, not messages), by kind. This is the number that decides whether first run feels usable
- Per-month spending totals, to compare against the user's own bank statements
- Per-account latest balance, to compare against the bank app

---

## 24. Assumptions to confirm

These fill gaps the discussion didn't cover. Confirm or change before building.

1. **Personal-sender filter:** phone-number senders are never read (section 3).
2. **Wallet detection:** wallets detected by wallet wording, then user-confirmed; fallback to a user-editable list (section 7).
3. **Wallet UPI vs balance phrases** need tuning on the dataset (section 11.3).
4. **A message with no account digits** is accepted only if the user has exactly one account at that sender and the message has a UPI reference or balance phrase; otherwise it is asked about as `not_sure` (section 8.0).
5. **Duplicate alert** = same account + same UPI ref + same amount + same direction + within 24 hours (section 11.2).
6. **Windows and limits:** label match ±30 min; self-transfer ±30 min, fee ≤ min(₹50, 2%); manual merge ±6 hours; duplicate alert 24 hours; catch-up every hour reading back 3 days; lent reminder 09:00 on the 1st.
7. **Over-repayment** splits into a repayment part and a normal credit (section 15.2).
8. **Body-date ignored;** SMS system time is the transaction time (section 6.6).
9. **Bodies of messages with no account evidence** are deleted after classification (section 22).
10. **Account digits:** the captured run is 3 to 12 digits; leading zeros are removed; two forms are the same account when one ends with the other (section 8.1).
11. **Merge thresholds:** same sender with 3 digits needs exactly one fitting account; across senders needs 4 or more matching digits to be counted at once and still asks the user to confirm (sections 8.0, 8.2).
12. **Looser grouping and matching of formats:** about 80% of masked words in common (sections 9.2, 9.4 and 18.1), to be tuned on the dataset. The amount position is counted from the start of the message; a message with fewer amount positions than the taught slot is treated as unreadable instead of guessed.
13. **Promo marker list, the completed-verb rule and the "not a payment event" list** (section 5, rows 3 and 5) are a starting set; they must be tuned on the dataset by reading the `promo`, `no_account` and `not_sure` groups.
14. **Balance regex** (section 6.7) is a starting pattern. Banks differ in how they print the balance; it must be tuned on real messages.
15. **Cards are not accounts with a spendable balance:** a message whose account keyword is `card` creates a `card` account, excluded from balances (section 16.4).
16. **Daily limit** can be skipped at first launch; a change applies from now, with no history of past limits (section 16.3).
17. **Limit-clause pattern and date-shaped digit check** (sections 6.1 and 6.3) are written from the shapes seen in another app's reported failures, not from our own messages; confirm both on the dataset.
18. **A deleted transaction is matched by `raw_sms_id` or `content_hash`** (section 19). A user who deletes a transaction and later wants the same SMS back uses undo; there is no "re-import this message" action in v0.

---

## 25. Revision log

- **2026-11-11:** folded in six changes after comparing with another open-source SMS tracker (`claude/logic_2.md`, section 20.4):
  1. Whole `Avl Limit … amount` clause removed before amount extraction (6.1).
  2. More non-transaction wordings in the pre-filter: blocked funds, due reminders, EMI-conversion offers, voucher delivery, merchant acknowledgements, auto-debit intimations (5).
  3. Account digits that are really a date are rejected (6.3, invariant 13).
  4. 24-hour window on the duplicate-alert check (11.2, invariant 2).
  5. A deleted transaction is never re-created by a rescan or reprocess (2, 10, 19, 20, invariant 12).
  6. Catch-up scan reads back 3 days instead of 1 (3, 20).
- Also added to the dataset report: the number of review cards a fresh import produces (23).
