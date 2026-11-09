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
- A PR that adds or changes a screen updates the [Figma](https://www.figma.com/design/mO0rO29TWFRdEhGfm0NPDT/Shifter-Design) before it is merged. If review changes alter the UI again, the Figma is updated again before the merge.

## How to work

- Make one **bounded, reviewable** change per PR. If it sprawls across unrelated files, split it.
- Read the failing tests carefully and iterate until `./gradlew check` passes.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- Commit, branch and open PRs as described in "Commits, branches and PRs" below.
- **Acknowledge your contributors** at the top of the file: credit the AI that wrote it with a `Co-authored-by` line. 

## Commits, branches and PRs

Agreed by the team in #334.

- **Commits**: `<type>: <Subject>`, where the type is `feat`, `fix`, `refactor`, `test`, `style`, `docs` or `chore`, and the subject is imperative, capitalized, at most 50 characters including the type (e.g. `fix: Show Shifter as the name of the app`).
  - When the subject is not enough, add a body wrapped at 72 characters, in two paragraphs starting with `What:` and `Why:`.
  - One kind of change per commit: a refactor and a feature go in separate commits.
  - End with a `Co-Authored-By:` trailer when an AI wrote part of the commit.
- **Branches**: `<type>/<issue>-<slug>`, in lowercase with hyphens, where the type is the one of the main change: `feature`, `fix`, `refactor`, `test`, `style`, `docs` or `chore` (e.g. `fix/325-app-name`).
  - Always branch from `main`, and update with `git rebase origin/main`, never by merging `main`. Then push with `git push --force-with-lease`, and never rebase a branch someone else is pushing to without telling them.
  - A PR that depends on another one waits for it to be merged, then branches from the updated `main`.
- **Pull requests**: titled like their main commit, and filled from `.github/pull_request_template.md`.
  - The Definition of Done copies the acceptance criteria of the issue, plus the items of "Definition of done" above. Only check what was actually verified; an unchecked box points to Out of scope or Known limitations.
  - End with `Closes #N`, or `Part of #N` when the issue is only partly done.
- **Merging**: "Create a merge commit" only, once the branch is rebased on `main` and the CI is green; the branch is deleted after the merge.
- **Issues**: titled in the imperative, and filled from the templates of `.github/ISSUE_TEMPLATE/` (task, bug, user story), with their project fields.

## Naming

- Follow the [Naming conventions](https://github.com/Shifter-Swent/shifter/wiki/Naming-conventions) of the wiki.
- Test functions are named `method_behavior`, or `method_condition_expected` when a condition matters (e.g. `addMission_withValidForm_createsTheMission`). For a screen or a flow, the first part names the action or the situation (e.g. `submit_withEmptyTitle_showsTheError`, `loading_showsTheSpinner`).

## Strings

- Put the strings of a feature in `res/values/strings_<feature>.xml` (e.g. `strings_mission.xml`), in the file of the feature that owns them; create it if needed.
- Keep `strings.xml` for the strings shared by several features (e.g. `app_name`, `content_description_back`).
- A string reused by a step of the same flow stays in the file of the feature that owns it (e.g. `create_event_title`, also used by the team setup of the event creation); move it to `strings.xml` only when unrelated features use it.

## Packages names

- Only add files in android.shifter, do no create a new package swent.shifter, do not modify existing files about packages names.
- At the top of the file for the package name, use swent.shifter, not android.shifter
