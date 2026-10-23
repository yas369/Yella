# SMS Ledger: Design System (Material 3 Expressive)

Implements the flows in [`flows.md`](flows.md). Code: `app/src/main/java/com/smsledger/ui/theme/` and `ui/components/`. Widget: `app/src/main/java/com/smsledger/widget/`.

## Direction

**Native Android, Material 3 Expressive.** The app should feel like it shipped on the phone: it uses the user's wallpaper colours, Android's navigation and app-bar patterns, and Expressive shapes and motion. One thing stays fixed regardless of colour: the **meaning** of spending status.

| Principle | In practice |
|---|---|
| Belongs on the phone | Dynamic colour (Material You), short navigation bar, large flexible top app bars, search app bar, predictive back, edge-to-edge, home-screen widget, launcher shortcut, themed icon |
| The one number matters most | Today has no app bar; the hero (80sp, SemiBold, tabular figures) is the title |
| Status is fixed, not themed | Amber = 80% of the limit, red = over. Fixed colours (`LedgerColors`), always paired with words ("81%", "over limit") |
| Expressive, not decorative | Shape and motion carry meaning: a cookie-shaped count, buttons that morph when pressed, keys that squash, a wavy bar for import progress |

## Colour

| Source | When |
|---|---|
| `dynamicLight/DarkColorScheme` | Android 12+, "Dynamic colour" on (default) |
| Brand "ledger" scheme (`LightColors` / `DarkColors`: paper `#F7F5F0`, ledger green `#1E5B4A`) | Android 8–11, or dynamic colour switched off |
| Fixed status colours | Always: warning `#B06500` / `#FFB95C`, over `#BA1A1A` / `#FFB4AB`, credit `#1F7A55` / `#7FD8AE` (light / dark) |

Role usage: `primary` for main actions and the meter fill; `secondaryContainer` for the meter track and unselected toggle buttons; `tertiaryContainer` for the Needs you card; `surfaceContainer` for cards and grouped settings rows.

## Type

The Material 3 Expressive scale (`Typography()`), using the **Emphasized** styles for titles, card headings and section labels. All money uses tabular figures (`tnum`):

| Role | Style |
|---|---|
| Today hero | `displayLargeEmphasized` → 80sp SemiBold, −2sp tracking |
| Large amounts (sheets, History month) | `displaySmallEmphasized` |
| Row amount | `titleMediumEmphasized` |
| Screen titles (onboarding) | `headlineMediumEmphasized` / `headlineLargeEmphasized` |
| Section labels | `titleSmallEmphasized` in `primary` |

## Shape

| Element | Shape |
|---|---|
| Buttons | Expressive `ButtonDefaults.shapes()`: a pill that morphs to a rounded square while pressed |
| Choosing one of a few | Connected button group (`ToggleButton` + `ButtonGroupDefaults.connected*Shapes`); the selected button rounds fully |
| Cards, sheets | `extraLarge` (28dp) |
| Settings rows | Grouped: 24dp outer corners, 4dp inner corners, 2dp gaps |
| Badges and empty states | `MaterialShapes`: Cookie9 (Needs you count), Cookie6 (person avatar), Sunny (onboarding icon), SoftBurst (all caught up) |
| Keypad keys | Tonal pills that squash to 14dp corners on press |

## Motion

`MotionScheme.expressive()` throughout. Spring specs from the motion scheme drive the meter fill (`slowSpatialSpec`), colour changes (`defaultEffectsSpec`) and key squash (`fastSpatialSpec`). Cards leave the Needs you queue with `animateItem()`.

## Signature components

| Component | Notes |
|---|---|
| `LimitHero` + `DayMeter` | Expressive progress anatomy: fill, 4dp gap, track, a gap at 80%, and a stop dot. The geometry is a pure function (`meterSegments`, unit-tested) |
| `ConnectedChoice` | Mine \| Not mine, Spent \| Received, Money out \| Money in, theme |
| `BigChoice` | Full-width 56dp morphing button for one-tap decisions |
| `NumberPad` | Squashing pill keys with haptic feedback |
| `ShapeBadge` / `LedgerShapes` | Content inside named Expressive shapes |
| Home-screen widget (Glance) | 2×1: amount, label and meter. Taller sizes add "₹x of ₹y" and a + (Add expense). Uses Glance's dynamic colour and the fixed status colours |

## Spacing

4-pt scale: 4 · 8 · 12 · 16 · 24 · 32 · 48. Screen gutter is 16dp (the Material compact-width margin).

## Accessibility

Tap targets ≥ 48dp (decisions ≥ 56dp). Status always has words as well as colour. The hero is read by TalkBack as one phrase. Toggle groups expose radio-button semantics. Light and dark, with dynamic colour in both, are checked on the emulator.

## Rejected

Pinning the app to one fixed palette (rejected in favour of dynamic colour), iOS-style segmented controls, centred iOS titles, custom navigation chrome, shadows for depth, and status colours that change with the wallpaper.
