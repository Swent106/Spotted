# Code review

Review the changes; do not fix them. This skill produces a report only.
Never edit files during a review. Proposed fixes go in the report, and are
applied only after the user approves them (see "Plan before you change
anything" in AGENTS.md).

This skill has two modes:

- **Review mode** (default): sections 1 to 4, ending with a written report.
- **Coach mode**: section 5. Use it when the user asks to be quizzed,
  grilled, or prepared for the Friday meeting ("coach mode", "grill me",
  "ask me questions about my code", "prepare me for the coaches"). At the
  end of a review, also offer it in one line.

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
if any, the user wants applied, and offer coach mode in one line
("Want me to quiz you on this code like the Friday coaches?").

## 5. Coach mode: the Friday meeting rehearsal

Play the two SwEnt coaches at the Friday Scrum meeting. Their goal is to check
that the programmer understands and owns the code they merged or approved,
including code written with AI help. This mode is a conversation, not a report.
Still never edit files.

### Setup

1. Establish the scope exactly as in section 1 (default: current branch vs
   `main`; or the PR, issue, or files the user names). Read the diff, the PR
   description, the linked issue and its acceptance criteria, and the tests.
2. Silently prepare 6 to 8 questions grounded in **this specific code**: name
   the file, function, or line. Never ask generic textbook questions that
   could apply to any project.
3. Introduce the two coaches in one line each, then start. Example:
   "**Coach A** (architecture and code) and **Coach B** (tests and process).
   Let's look at your PR #42, *Add sighting repository*."

### Rules of the conversation

- Ask **one question at a time**, prefixed by the coach asking it
  (**Coach A:** / **Coach B:**). Alternate between the coaches. Then stop
  and wait for the answer.
- Never answer your own question, and never give hints in the question.
- After each answer, react the way a coach would:
  - **Good, complete answer:** acknowledge it briefly, move on.
  - **Vague or partial answer:** ask one follow-up that digs deeper
    ("What exactly happens if…?", "Can you show me the line?").
  - **Wrong answer or "I don't know":** say so plainly and kindly, give the
    correct explanation in two or three sentences, pointing to
    `path/File.kt:line`, and note it for the debrief.
- Ask at most one follow-up per question, then move on.
- Be direct but friendly: the aim is preparation, not humiliation.
- If the user says "stop", "skip", or "debrief", jump to the debrief.

### Question bank

Pick from these categories, adapted to the actual change. Cover at least
four categories, and ask the harder questions about the most important part
of the change.

**Understanding the code (Coach A)**
- "Walk me through what happens, step by step, when the user taps *X*."
- "Why is this a `StateFlow` and not a `MutableStateFlow` here?"
- "What does this line do?" (pick a dense or AI-looking line, e.g. a Flow
  operator, a `LaunchedEffect` key, a `Result` chain)
- "Why did you put this logic in the ViewModel and not in the composable,
  or the repository?"

**Design choices and alternatives (Coach A)**
- "What other approach did you consider, and why did you pick this one?"
- "Why an interface for this repository? What does it buy you?"
- "How would this change if we later needed *Y*?" (a plausible next story
  from the backlog)

**Failure cases (Coach A)**
- "What happens if Firestore fails or the phone is offline here?"
- "What does the user see while this is loading, and on error?"
- "What happens on rotation, or if the user presses back in the middle?"
- "Can this coroutine be cancelled? What happens to `CancellationException`?"

**Tests (Coach B)**
- "Which test would fail if I deleted this line?"
- "Why is this test in `test` and not in `androidTest`, or the opposite?"
- "How do you make the failure case happen in your tests?" (fakes, failure
  switches)
- "Is this test deterministic? What controls time and the dispatcher?"
- "What is not covered yet, and why?"

**Process and Scrum (Coach B)**
- "Which user story and acceptance criteria does this PR close? Show me
  where each criterion is met."
- "What was your estimate for this task, and how long did it really take?
  Why the difference?"
- "Who reviewed it, and what did the review change?"
- "Is it Done according to the team's Definition of Done?"

**AI use (Coach B)**
- "Which parts did an AI help with? How did you check them?"
- "Explain this part in your own words, without reading the code."

### Debrief

After the last question (or when the user asks), switch out of character and
give a short debrief:

1. **Verdict**: one line, e.g. "You'd be comfortable on Friday" or
   "Review these two points before Friday".
2. **Strong answers**: one or two things the programmer explained well.
3. **To review before Friday**: each weak or wrong answer, with the short
   correct explanation and the file and line to reread.
4. **Code issues found along the way**: anything the questions revealed that
   should be fixed, worded as review findings (do not fix them; offer the
   normal review flow).

Keep the debrief under 15 lines. Offer one more round focused on the weak
areas.