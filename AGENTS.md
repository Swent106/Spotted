# AGENTS.md

Durable rules for any AI agent working in this repository. Read this before acting.

## The app

A Kotlin/Android app (`<package>`) built with an **MVVM** architecture.

- `model/` holds data classes and repositories (Firestore, Location, ...).
- `ui/` holds screens and their **ViewModels**.
- Repository implementations are wired into ViewModels in `<where DI/factories live>`.

## Plan before you change anything

Before editing any file, explain your plan in plain English and wait for approval.
Do not show diffs, create files, or run commands that modify the repo until the
user explicitly approves (e.g. "go", "approved", "yes").

The plan must include:
- **Goal**: one or two sentences on what you're trying to achieve.
- **Changes**: each file you will touch, and what you will change in it and why.
- **Not touching**: anything nearby you will deliberately leave alone.
- **Risks / questions**: anything uncertain, or any decision the user should make.

Keep it short: a reader should understand it in under a minute. No code in the
plan unless a signature or interface change needs to be shown.

Once approved:
- Change only what the plan describes. If you discover you need to touch another
  file or do something different, stop, explain why, and wait for approval again.
- After the changes, summarize in plain English what you did, and call out
  anything that differs from the approved plan.

## Architecture rules

- Keep the MVVM separation. **ViewModels never import Firebase** or a repository
  implementation; they depend on repository interfaces. Firebase lives only in
  `model/` repositories.
- Do not edit generated code (`build/`, `<other generated paths>`).
- Never commit secrets: `local.properties`, API keys, or changes to
  `google-services.json`.

## Tests

This project has two test source sets:

- `app/src/test/`: local unit tests. Run on the JVM, no device needed.
  Command: `./gradlew testDebugUnitTest`
- `app/src/androidTest/`: instrumented tests (integration and E2E). Need a
  running Android emulator or device, and the Firebase emulator.
  Command: `./gradlew connectedDebugAndroidTest`

When to run them:

1. **Before changing anything**, run the unit tests and note any failures. This
   is the baseline, so pre-existing failures are not blamed on your change.
2. **While working**, run the unit tests after each meaningful change.
3. **Before saying the task is done**, run both suites:
  - `./gradlew check` (unit tests + lint) and `./gradlew ktfmtCheck`
  - `./gradlew connectedDebugAndroidTest`, but first check `adb devices`.
    If no device is listed, do not skip silently: say that instrumented tests
    were not run and why.

Reporting:

- Report the results of both suites in plain English: what passed, what failed,
  and what could not be run.
- Clearly separate **new failures** (caused by your change) from **failures
  already present in the baseline**.
- If a test fails, explain the likely cause and propose a fix, but do not change
  the test or the code under test to make it pass without approval.


## Definition of done

- The change meets its acceptance criteria.
- `./gradlew check` (unit tests + lint) and `./gradlew ktfmtCheck` pass.
- The relevant higher-level tests (see table) pass, or you have reported that
  you could not run them.

## How to work

- One bounded, reviewable change per PR. If it sprawls across unrelated files,
  split it.
- Stage only the files you changed; never `git add .` or `git add -A`.
- Commit with an imperative, capitalized subject of at most 50 characters
  (e.g. `Add user authentication`), and a body wrapped at 72 characters when
  needed. End the message with a `Co-authored-by:` trailer crediting the AI agent.

## Responsibility

The human sets the goal, context, and acceptance criteria, reviews the diff, and
owns every line submitted. The agent's job is to make that review easy: small
diffs, clear commits, and an honest report of what was and wasn't verified.