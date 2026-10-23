# SMS Ledger: Design System

Implements the flows in [`flows.md`](flows.md). Code: `app/src/main/java/com/smsledger/ui/theme/` and `ui/components/`.

## Direction: "paper ledger"

A calm, trustworthy money tool, not a fintech ad. The app is opened many times a day for about 2 seconds, so the one number matters more than anything else.

- **Warm paper neutrals** (`#F7F5F0`) instead of stark white, and ink (`#1C1B18`) instead of pure black. This reads like a notebook, not a dashboard.
- **One brand colour: ledger green** (`#1E5B4A`). It is used for primary actions and income only, never as decoration.
- **Status colours carry meaning only:** amber at 80% of the limit, brick red for over the limit. Each one always comes with words ("over limit"), so colour is never the only signal.
- **No gradients, no glass, no drop shadows.** Depth comes from tonal surfaces (`surfaceContainer*`).
- **Corner radii follow a hierarchy:** 8 for chips, 12 for buttons, 16 for cards, 28 for sheets. Not everything is a pill.

## Rejected
Purple/indigo palettes, hero gradients, card grids on Today, pie charts (there are no per-category limits in v0), negative numbers ("−₹120 left"), and confirmation dialogs for known cases.

## Type
System sans (Roboto Flex on Android 12+). All money uses **tabular figures** (`tnum`) so digits don't jump as totals change.

| Role | Style |
|---|---|
| Today hero amount | 72sp Medium, -2sp tracking |
| Screen title | headlineSmall |
| Row payee | bodyLarge Medium |
| Row amount | bodyLarge Medium, tnum |
| Meta (account · time) | bodyMedium, onSurfaceVariant |

## Spacing
4-pt scale: 4 · 8 · 12 · 16 · 24 · 32 · 48. Screen gutters are 20dp, which keeps the hero away from curved screen edges.

## Signature component: Day meter
A thick rounded track with a tick at 80%, filled in green, then amber, then red. Below it: "₹160 spent of ₹500". The hero number is announced to TalkBack as a single phrase.

## Interaction rules
- One tap per decision. Big full-width choices in the Needs-you queue and in sheets.
- Every change shows an Undo snackbar.
- Sheets for quick tasks (add, edit, teach). Full screens for lists (Needs you, Owed, History).
- Light and dark are designed together. Dark is warm charcoal, not blue-black.
