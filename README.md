# SMS Ledger

On-device Android app that builds a spending ledger from bank SMS and shows how much you can still spend today.

- Spec: `sms-ledger-v0-features.md` (kept outside the repo)
- Plan: [`tasks/plan.md`](tasks/plan.md) · Tasks: [`tasks/todo.md`](tasks/todo.md) · Flows: [`docs/flows.md`](docs/flows.md)

## Modules

| Module | What |
|---|---|
| `core/` | Pure Kotlin/JVM: parsing, ledger rules, formatting. No Android code. |
| `tools/corpus/` | Developer CLI that runs `core` over an SMS XML export. Never shipped. |
| `app/` | Android app: Room, SMS reading, WorkManager, Compose UI. |

## Build

Requires the Android SDK (`local.properties` → `sdk.dir`) and a JDK. Gradle 9.8 runs on JDK 17–25; Android Studio's bundled JBR works:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

| Task | Command |
|---|---|
| Core tests | `./gradlew :core:test` |
| App unit tests (both builds) | `./gradlew testOfflineDebugUnitTest testSmsDebugUnitTest` |
| Debug APKs | `./gradlew assembleOfflineDebug assembleSmsDebug` |
| Corpus report | `./gradlew :tools:corpus:run --args="<report> <path-to-export.xml>"` |

SMS exports go in `data/` (gitignored). Never commit real messages.

## Builds

Two builds from one codebase. Both share the application id and debug key, so installing `sms` over `offline` keeps your data.

| Build | SMS permissions | How to install | What it does |
|---|---|---|---|
| `offline` | None | Open `app/build/outputs/apk/offline/debug/app-offline-debug.apk` on the phone | Manual ledger: daily limit, categories, lent, history, widget, alerts. Reads nothing. |
| `sms` | `READ_SMS`, `RECEIVE_SMS`, asked at runtime only after the in-app disclosure | Over USB: `./gradlew installSmsDebug`. Later: Play Store, after Google approves the SMS Permissions Declaration. | Everything in `offline`, plus automatic capture from bank SMS. |

Google Play Protect blocks apps that *declare* SMS permissions when they're installed from a file (browser, Files, chat apps). That check runs at install time, before the app can show its consent screen, which is why the `sms` build installs over USB or from the Play Store instead.

**Privacy, both builds:** no `INTERNET` permission, no account, no analytics, Android backup and device transfer disabled. SMS are processed on the phone only, and only messages from accounts you confirm are stored.
