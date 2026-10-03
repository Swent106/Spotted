# Git Guide — Spotted (SwEnt)

Everything the team needs for Git on this project. Rule zero: **nobody pushes to `main`; everything goes through a PR.**

---

## 1. One-time setup

```bash
git clone git@github.com:<org>/<repo>.git
cd <repo>
git config user.name "Your Name"
git config user.email "you@epfl.ch"
git config pull.rebase true        # `git pull` rebases instead of creating merge commits
```

### Never commit these (check `.gitignore`)

```gitignore
local.properties            # SDK path + Maps API key
app/google-services.json    # Firebase config (CI recreates it from a GitHub secret)
*.keystore
*.jks
build/
.gradle/
.idea/
.DS_Store
```

### Protect `main` (GitHub → Settings → Branches → Branch protection rule)

- Require a pull request before merging, with **at least 1 approval**
- Require status checks (CI) to pass
- Require branches to be up to date before merging
- Block force pushes and deletion

---

## 2. Daily workflow

```bash
# 1. Start from an up-to-date main
git switch main
git pull

# 2. Create your task branch (naming rules in AGENTS.md)
git switch -c feature/12-sign-in-screen

# 3. Work, then commit in small logical steps
git status
git add <files>              # avoid `git add .` blindly; check what you stage
git commit                   # opens the editor: subject, blank line, body

# 4. Push and open a PR
git push -u origin feature/12-sign-in-screen
```

Then on GitHub:

1. Open the PR as a **Draft** to get early feedback and run CI without notifying reviewers.
2. Self-review your own diff.
3. Mark it **Ready for review** and request a reviewer.
4. Address the comments, push the fixes to the same branch, and reply to each thread.
5. Once it's **approved and CI is green**, the **author** merges it.
6. Delete the branch (GitHub offers a button).

**Deadline rule:** each PR must be ready for review **at least 24h before the end of the Sprint**, and merged before the Sprint ends.

---

## 3. Branch names and commit messages

See `AGENTS.md` for the full rules. In short:

- **Branch:** `<type>/<issue>-<short-description>`, with type ∈ `feature | fix | test | refactor | docs | chore`
  e.g. `feature/18-home-nearby-alerts`
- **Commit subject:** imperative, ≤ 50 characters, capitalized, no period, e.g. `Add home area picker to personal info screen`
- **Commit body:** wrap at 72 characters; explain what and why, not how; end with `Closes #18`

---

## 4. Keeping your branch up to date

When `main` has moved since you created your branch:

### Option A: rebase (default for your own branch)

```bash
git fetch origin
git rebase origin/main
# resolve conflicts if any (section 5), then:
git push --force-with-lease
```

- Your commits are replayed on top of `main`, giving a linear history.
- It rewrites your commits (new hashes), which is why you need a force push. Always use `--force-with-lease`, never plain `--force`: it refuses to overwrite work you haven't seen.

### Option B: merge `main` into your branch

```bash
git fetch origin
git merge origin/main
git push
```

- **Use this if someone else has already pulled your branch** or built on it.
- **Golden rule: never rebase a branch someone else is working on.**

---

## 5. Resolving conflicts

Git marks the conflicting parts of each file:

```text
<<<<<<< HEAD
your version
=======
the other version
>>>>>>> origin/main
```

1. Edit the file so it contains the correct final code, and delete the markers.
2. Build and run the tests. A conflict can be resolved in a way that compiles but is wrong.
3. Mark the file as resolved and continue:

| During a… | Continue | Give up |
|---|---|---|
| rebase | `git add <file>` then `git rebase --continue` | `git rebase --abort` |
| merge | `git add <file>` then `git commit` | `git merge --abort` |

If the conflict is in someone else's code and you're unsure, ask them rather than guessing.

---

## 6. Pull requests

- **Small and single-purpose.** One task = one PR. View and ViewModel are separate PRs.
- **Title:** what the PR does, e.g. `Add sign-in ViewModel`.
- **Description template:**

```markdown
## What
Short summary of the change.

## Why
Link to the task: Closes #12

## How to test
Steps or the tests added.

## Notes
Anything the reviewer should look at closely, decisions taken, follow-ups.
```

- Link the issue so it closes automatically when the PR is merged.
- **Don't merge with red CI**, even if "the failing test is just flaky." Fix the flaky test instead.

### Merge strategy (choose one as a team)

We recommend **squash merge**: each PR becomes a single commit on `main` titled with the PR name, which keeps history readable. The trade-off is that the individual commits of the branch don't appear on `main`.

---

## 7. Reviewing a teammate's PR

```bash
gh pr checkout 42      # GitHub CLI; or: git fetch origin && git switch <their-branch>
./gradlew check        # build, lint, and tests locally
```

- Check scope (does it match the title?), correctness, tests that actually assert something, naming, and architecture (e.g. no Firebase in ViewModels).
- Review ≤ 1 hour at a time, roughly 200–500 lines per hour.
- Comment on the code, not the person. Explain why, and suggest a fix.
- Leave formatting to the tools (ktfmt and lint in CI).
- When you approve, you share responsibility for what gets merged.

---

## 8. Fixing mistakes

| Situation | Command |
|---|---|
| See what changed | `git status`, `git diff`, `git diff --staged` |
| Unstage a file | `git restore --staged <file>` |
| Discard local changes to a file | `git restore <file>` (⚠ irreversible) |
| Fix the last commit's message, or add a forgotten file (not pushed yet) | `git add <file>` then `git commit --amend` |
| Put work aside to switch branches | `git stash`, then `git stash pop` later |
| Undo a commit that is already on `main` | `git revert <hash>` (creates a new commit that undoes it) |
| Undo local commits that aren't pushed | `git reset --soft HEAD~1` (keeps your changes staged) |
| "I lost my commits" | `git reflog` to find the hash, then `git switch -c rescue <hash>` |

- **Never** use `git reset --hard` or a force push on `main` or on a shared branch.
- **If you commit a secret** (API key, `google-services.json`): **rotate the key immediately**. Deleting the file in a new commit isn't enough, because it stays in the history.

---

## 9. Working with AI agents

- The agent works on a **task branch**, never on `main`.
- Read every commit before pushing it. You own the code, not the agent.
- Don't let the agent force-push, rebase shared branches, or delete branches.
- If the agent produces a huge diff, reject it and split the task. Don't try to review 800 lines.
- Agent commits follow the same commit-message rules.

---

## 10. Cheat sheet

| Goal | Command |
|---|---|
| Update main | `git switch main && git pull` |
| New branch | `git switch -c feature/12-short-name` |
| Commit | `git add <files> && git commit` |
| First push | `git push -u origin <branch>` |
| Update my branch with main | `git fetch origin && git rebase origin/main` |
| Push after a rebase | `git push --force-with-lease` |
| Switch branch | `git switch <branch>` |
| History | `git log --oneline --graph --all` |
| Who wrote this line | `git blame <file>` |
| Delete a merged branch locally | `git branch -d <branch>` |
| Clean up deleted remote branches | `git fetch --prune` |
