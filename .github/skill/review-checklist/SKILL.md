---
name: code-review
description: Review code changes in the Spotted Kotlin/Android app against the team checklist (scope, MVVM architecture, Kotlin and coroutines, Compose UI, Firebase, tests, security, style). Use this skill whenever the user asks to review a diff, a branch, a PR, staged changes, or "my changes", or asks whether something is ready to merge or submit, even if they don't say "code review".
---

# Code review

Review the changes; do not fix them. This skill produces a report only.
Never edit files during a review. Proposed fixes go in the report, and are
applied only after the user approves them (see "Plan before you change
anything" in AGENTS.md).

## 1. Establish what is being reviewed

- Default scope: the current branch compared to `main`
  (`git diff main...HEAD` and `git diff --name-only main...HEAD`).
  If the user names a PR, commit range, or staged changes (`git diff --staged`),
  use that instead.
- Read the PR description, linked issue, or approved plan if one exists. The
  review checks the change against its stated goal, not just the code in isolation.
- Before the checklist, write two or three plain-English sentences on what the
  change does. If you cannot summarize it, that is itself a finding.

## 2. Run the mechanical checks

Run these and record the results. If one cannot run (no emulator, no network),
say so in the report. Never report a check as passing if you did not run it.

```
./gradlew check          # unit tests + lint
./gradlew ktfmtCheck     # formatting
```

Run instrumented tests (`./gradlew connectedDebugAndroidTest`) only if the change
touches repositories, Firebase, or user-visible flows, and only if the Android
emulator and Firebase emulator are available.

Quick greps for architecture violations:

```
git diff main...HEAD -- '*/ui/*' | grep -E '^\+.*import com\.google\.firebase'
git diff main...HEAD | grep -E '^\+.*(!!|GlobalScope|runBlocking|Thread\.sleep)'
git diff --name-only main...HEAD | grep -E 'local\.properties|google-services\.json|/build/'
```

## 3. Checklist

### Scope
- [ ] The change does what its goal/plan says, and nothing unrelated.
- [ ] No stray files: `local.properties`, `build/` output, IDE files, secrets.
- [ ] No test was deleted, `@Ignore`d, or had its assertions weakened; no new
  `@Suppress` or lint baseline entries added to make the build pass.

### Architecture (MVVM)
- [ ] ViewModels import no Firebase classes and no repository implementations;
  they depend on repository interfaces only.
- [ ] Firebase code lives only in `model/` repositories.
- [ ] Composables hold no business logic; they render state and forward events.
- [ ] ViewModels hold no `Context`, `Activity`, or `View` references.
- [ ] UI state is exposed as immutable state (`StateFlow`, not `MutableStateFlow`)
  from the ViewModel.

### Kotlin and coroutines
- [ ] No `!!` without a clear reason; nullability is handled explicitly.
- [ ] Coroutines launch in `viewModelScope` (or another lifecycle-aware scope),
  never `GlobalScope`; no `runBlocking` in app code.
- [ ] Blocking or I/O work is off the main thread.
- [ ] Failures from Firestore, location, and network calls are caught and turned
  into UI state (error message, retry), not swallowed or left to crash.
- [ ] Names are clear; no dead code, commented-out code, or leftover debug logs.

### Compose UI
- [ ] Flows are collected with `collectAsStateWithLifecycle` (or `collectAsState`).
- [ ] State survives recomposition (`remember`) and, where needed, configuration
  change (`rememberSaveable` or the ViewModel).
- [ ] User-facing text comes from string resources, not hardcoded literals.
- [ ] Interactive elements and images have `contentDescription` where meaningful.
- [ ] Elements that tests need to find have test tags.

### Firebase and data
- [ ] Firestore reads/writes handle missing documents and malformed data.
- [ ] Snapshot listeners are removed when no longer needed.
- [ ] If data access patterns changed, Firestore security rules were updated
  and still deny what they should.
- [ ] Location data is requested only with the right permission flow and is not
  logged or stored beyond what the feature needs.

### Tests
- [ ] New or changed logic has unit tests (ViewModels, repositories with fakes).
- [ ] Repository or boundary changes have integration tests against the emulator.
- [ ] Changed user-visible flows have an updated or new E2E test.
- [ ] Tests are deterministic: `runTest` and test dispatchers, no `Thread.sleep`,
  no dependence on test order or real network.
- [ ] Tests assert behaviour, not implementation details.

### Security
- [ ] No API keys, tokens, or credentials in code, resources, or commits.
- [ ] No personal data (emails, locations, user IDs) written to logs.

### Commits
- [ ] Subjects are imperative, capitalized, at most 50 characters; bodies
  wrapped at 72.
- [ ] AI-written commits end with a `Co-authored-by:` trailer.

## 4. Report format

Write the report in plain English, in this order. Keep it scannable; do not
paste large diffs. Reference code as `path/File.kt:line`.

1. **Summary**: what the change does, in two or three sentences, and an overall
   verdict: *ready*, *ready after small fixes*, or *needs changes*.
2. **Checks run**: each command and its result, including any you could not run.
3. **Blocking**: issues that must be fixed before merge (broken architecture
   rules, failing checks, bugs, security problems, weakened tests). For each:
   where, what is wrong, why it matters, and a suggested fix.
4. **Should fix**: real problems that are not blocking.
5. **Nits**: minor style or naming points. Keep this short.
6. **Questions**: anything that depends on intent you cannot infer.

Skip any empty section rather than writing "none". End by asking which fixes,
if any, the user wants applied.