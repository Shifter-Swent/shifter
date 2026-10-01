---
name: code-review
description: Team code-review checklist for Shifter (Android, Kotlin, Jetpack Compose, MVVM, Firebase). Use this skill whenever reviewing a pull request, a diff, a branch or a code change in this repository, or when asked "is this ready to merge?", "review my code" or "check this PR", even if the word "review" is not used explicitly.
---

# Shifter code review

Review changes the way a careful teammate would: catch real problems, explain *why* they matter,
and keep the PR mergeable. Seven people work on this codebase, so consistency and small,
isolated changes matter as much as correctness.

## Process

1. Read the PR description and the linked issue. If there is no linked issue or the goal is unclear, say so first.
2. Check the CI status (build, lint, tests, SonarCloud). A red CI is always blocking.
3. Read the diff, then go through the checklist below. Only report items that actually apply.
4. Write the review using the output format at the end of this file.

## Checklist

### Scope and PR hygiene
- The PR does one thing and matches its issue. Unrelated changes (refactors, formatting of untouched files) belong in a separate PR.
- The PR is small enough to review (rough guide: under ~400 changed lines excluding tests and generated files). Otherwise suggest how to split it.
- Branch name and commit messages follow the team convention and are in English.
- No commented-out code, leftover debug logs (`Log.d`, `println`), or `TODO`s without a linked issue.

### Architecture (MVVM)
- Composables never access Firebase or repositories directly. The flow is UI → ViewModel → Repository (interface) → Firebase implementation.
- Repositories are accessed through interfaces so they can be replaced by fakes in tests. No `FirebaseFirestore.getInstance()` inside a ViewModel.
- ViewModels expose immutable state (`StateFlow<UiState>`), not `MutableStateFlow` or mutable collections.
- ViewModels do not hold a `Context`, `Activity` or any View/Compose reference.
- Business logic (e.g. "is this shift full?", "can this volunteer sign up?") lives in the ViewModel or a domain/model class, not in a composable.
- Changes to the shared data model (Firestore collections, document fields, model classes) are flagged explicitly: they affect the whole team and must have been agreed on.

### Jetpack Compose
- Screen composables are split into a stateful wrapper (gets the ViewModel) and a stateless content composable (takes state + callbacks), so the content can be previewed and tested.
- State is hoisted; no business state stored with `remember` inside deeply nested composables.
- Interactive and asserted elements have a `testTag`, defined as constants (not string literals scattered in tests).
- User-facing strings come from `strings.xml`, not hardcoded.
- Lists use `LazyColumn`/`LazyRow` with stable `key`s.
- No heavy work or side effects directly in composition; use `LaunchedEffect` or the ViewModel.

### Kotlin
- No `!!` unless the non-null guarantee is obvious and commented.
- Prefer `val`, immutable data classes and `when` over long `if/else` chains.
- Coroutines are launched in `viewModelScope` (or an injected scope), never `GlobalScope`. Dispatchers are injectable so tests can control them.
- Errors from Firebase calls are handled and surfaced to the UI state (loading / success / error), not silently swallowed.
- Names are clear and consistent with the rest of the codebase.

### Firebase
- Collection and field names are defined in one place (constants), not duplicated as string literals.
- Queries are bounded (filters, `limit`) and do not load whole collections on the client.
- If the data model or access pattern changes, Firestore security rules are updated accordingly.
- No secrets, API keys or credentials committed. Follow the team policy for `google-services.json`.

### Tests
- New logic comes with unit tests (ViewModels tested with fake repositories).
- New screens come with UI tests using the test tags.
- Tests cover failure and edge cases (empty list, network error, full shift, cancelled event), not just the happy path.
- No `Thread.sleep` or timing-dependent tests; use test dispatchers and `waitUntil`.

## Output format

Start with a one- or two-sentence summary of what the PR does and your overall verdict:
**Approve**, **Approve with minor changes**, or **Request changes**.

Then group comments by severity, each with a `file:line` reference and a short explanation of *why*:

- **Blocking**: bugs, broken architecture rules, missing tests for new logic, red CI, security issues.
- **Should fix**: maintainability or consistency problems worth fixing before merge.
- **Nit**: style or naming suggestions; optional.

Be direct and specific. When suggesting a change, show a short code snippet. Do not pad the review with praise or restate the diff. If something is unclear, ask a question rather than assume.
