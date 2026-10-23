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
| App unit tests | `./gradlew :app:testDebugUnitTest` |
| Debug APK | `./gradlew assembleDebug` |
| Corpus report | `./gradlew :tools:corpus:run --args="<report> <path-to-export.xml>"` |

SMS exports go in `data/` (gitignored). Never commit real messages.
