# AGENTS.md

Durable rules for any AI agent working in this repository. Read this before acting.
Writing them down also helps the human team agree on how we build.

## The app

**Shifter** is Kotlin/Android application for volunteer recruitment and workforce management, built with an **MVVM** architecture using Jetpack Compose and Firebase.

- `model/` holds the data and repositories (Firebase Auth, Firestore, Cloud Storage, local cache/Room for offline mode, Location/GPS service, Camera/QR scanner, ...).
- `ui/` holds the screens and their **ViewModels**.

## Architecture rules

- Keep the **MVVM** separation. **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase lives only in the `model/` repositories.
- **Offline & Sync Contract:**
  - Essential data (personal schedule, mission address, role/briefing, volunteer QR code, and offline QR scans) must leverage local persistence.
  - Offline check-ins collected by Managers must be stored locally first and queued for automatic sync to Firestore upon reconnection.
- **Device Sensors:** GPS/Location services and Camera/Barcode APIs must be abstracted behind interfaces to allow headless unit/UI testing.
- **UI Decoupling:** Compose views must be stateless where possible, accept state as values, and emit user interactions via callbacks or events to ViewModels.

## Definition of done

- The feature works and matches its acceptance criteria and user story requirements.
- **All new code comes with tests:**
  - Unit tests for ViewModels, logic helpers, and repository fake implementations (`test/`).
  - Compose UI tests for screens and navigation flows (`androidTest/`).
  - Instrumented repository tests running against Firebase/Firestore emulators.
- `./gradlew check` is green (unit tests + lint) and `./gradlew ktfmtCheck` passes (formatting) before you submit.
- Features covered by instrumented tests (under `androidTest/`, e.g. the Firestore repository) must also pass `./gradlew connectedDebugAndroidTest`, with an Android emulator and the Firebase emulator running.

## How to work

- Make one **bounded, reviewable** change per PR. If it sprawls across unrelated files, split it.
- Read the failing tests carefully and iterate until `./gradlew check` passes.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- Commit with an imperative subject of at most 50 characters, capitalized (e.g. `Add user authentication`). Add a body wrapped at 72 characters when the subject is not enough.
- **Acknowledge your contributors** at the top of the file: credit the AI that wrote it with a `Co-authored-by` line. 
