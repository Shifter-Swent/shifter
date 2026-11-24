# AGENTS.md

Durable rules for any AI agent working in this repository. Read this before acting.
Writing them down also helps the human team agree on how we build.

## The app

**Shifter** is Kotlin/Android application for volunteer recruitment and workforce management, built with an **MVVM** architecture using Jetpack Compose and Firebase.

- `model/` holds the data and repositories (Firebase Auth, Firestore, Cloud Storage, local cache/Room for offline mode, Location/GPS service, Camera/QR scanner, ...).
- `ui/` holds the screens and their **ViewModels**.

## Architecture rules

- Keep the **MVVM** separation. **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase lives only in the `model/` repositories.
- Repositories are built only in `RepositoryProvider`. A ViewModel gets them through the `factory(...)` of its companion, so tests can pass fakes.
- A ViewModel exposes one immutable `StateFlow` of UI state (loading, content, empty, error) and never holds a `Context`.
- **Device Sensors:** GPS/Location services and Camera/Barcode APIs must be abstracted behind interfaces to allow headless unit/UI testing.
- **UI Decoupling:** Compose views must be stateless where possible, accept state as values, and emit user interactions via callbacks or events to ViewModels. Business rules never live in a composable.
- A change to the data model updates `docs/firestore-schema.md` and `firestore.rules` in the same PR.

## Offline and sync

- Essential data (personal schedule, mission address, role/briefing, volunteer QR code, and offline QR scans) must leverage local persistence.
- Offline check-ins collected by Managers must be stored locally first and queued for automatic sync to Firestore upon reconnection.
- A write the user can make offline uses `set`, `update` or a `WriteBatch`, never `runTransaction`, which fails offline.
- Key documents by their natural id (e.g. `checkIns/{uid}`) and update fields or use `arrayUnion`/`arrayRemove`, so a replayed write lands in the same place.
- Show writes that haven't synced as pending, from `metadata.hasPendingWrites()`. Every screen defines what it shows offline: never an endless spinner or an unexplained error.

## Robustness and security

- Validate every input in the ViewModel (trim, refuse empty values, cap lengths, check dates and ranges) and show the reason next to the field.
- Turn every repository failure into an error state with a retry. Never swallow an exception, and no `!!` without a comment.
- Disable a button while its action runs, to prevent double submits.
- `firestore.rules` gives each role the least access it needs. A risk the rules cannot close goes into "Known gaps" of `docs/firestore-schema.md`.
- Never commit secrets, and never log personal data or locations.

## User experience

- Use the theme and `ui/components`; don't hardcode colors, sizes or fonts.
- Every action shows its loading, success or error, and an empty state says what to do next.
- Strings come from resources (see "Strings"), icons have a `contentDescription`, and touch targets are at least 48 dp.

## Code quality

- Keep functions short, under Sonar's cognitive complexity limit of 15. Fix Sonar issues instead of suppressing them.
- Reuse an existing helper, component or repository method before writing one. No dead code, debug logs or `TODO` without an issue number.
- KDoc every public class and every non-obvious function: what it does and why.
- `@Preview` composables go in a `<Screen>Previews.kt` file.

## Definition of done

- The feature works and matches its acceptance criteria and user story requirements.
- **All new code comes with tests** (goal: 95 % line coverage on `main`):
  - Unit tests for ViewModels and logic helpers (`test/`), including edge cases and failures (repository error, offline). Never test a fake on its own: what the code relies on in a fake is tested through the ViewModels that use it, and a fake has no test file (#347).
  - Compose UI tests for screens and navigation flows (`androidTest/`), using their `TestTags`.
  - Instrumented repository tests running against Firebase/Firestore emulators, including what the security rules allow and refuse.
  - End-to-end tests (`androidTest/e2e/`): at least one per epic. A PR that completes a main flow extends or adds one.
  - A test checks what a class does through its public API, without widening it. Only when a test really needs direct access is it given `internal` rather than public, and never `internal` instead of `private` just for a test (#645).
  - No `Thread.sleep`: use `MainDispatcherRule`, test dispatchers and `waitUntil`.
- `./gradlew check` is green (unit tests + lint) and `./gradlew ktfmtCheck` passes (formatting) before you submit.
- Features covered by instrumented tests (under `androidTest/`, e.g. the Firestore repository) must also pass `./gradlew connectedDebugAndroidTest`, with an Android emulator and the Firebase emulator running.
- The CI is green, and Sonar shows no new issue and no drop in coverage.
- A PR that adds or changes a screen, or its test tags, updates its frames in the [Figma](https://www.figma.com/design/mO0rO29TWFRdEhGfm0NPDT/Shifter-Design) (Wireframe/Mockup and its Testing boards) before it is merged. If review changes alter the UI or its test tags again, the Figma is updated again before the merge.

## How to work

- Make one **bounded, reviewable** change per PR. If it sprawls across unrelated files, split it.
- Before planning an issue, read the [Architecture](https://github.com/Shifter-Swent/shifter/wiki/Architecture) page of the wiki: its tables list the repositories each feature's ViewModel uses, including what the current sprint plans. Follow it; if your plan adds or drops a dependency it shows, say so in the issue or the PR instead of diverging silently.
- Once a PR is merged that delivers a planned item, adds a feature nobody planned, or wires a screen in `ShifterNavHost.kt`, update the tables of the Architecture page, and redraw the diagram or ask a teammate to. Then link the wiki commit in a comment on the PR.
- Write the tests with the code. Read the failing tests carefully and iterate until `./gradlew check` passes; never weaken a test to make it pass.
- Before opening the PR, review your own diff with `.github/skills/SKILL.md`.
- In the "Design choices" of the PR, explain how the change fits the architecture, so the human author can explain every line.
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
  - Screenshots or a short video for every UI change.
  - Just before `Closes #N`, an `## Acknowledgement` section says which AI (name and model) did what in the PR, e.g. "Claude (Opus 5) wrote the fix and its tests and drafted this description, all under review. It is credited with a `Co-Authored-By:` trailer on the commits." Write "None." when no AI was used. The M3 grading requires every use of AI to be stated in the PR.
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
