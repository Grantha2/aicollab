# Migration: one repo → two projects

This repository has been split into two standalone projects that live, for
now, as top-level directories on this branch:

| Directory        | Becomes repo    | What it is |
|------------------|-----------------|------------|
| `conductor/`     | `conductor`     | On-rails, multi-agent SDLC copilot for non-engineers (grew out of the debate engine). |
| `cowork-suite/`  | `cowork-suite`  | Button-driven AI task suite for organisational leaders (the former "Executive Suite"). |

Each directory is a complete Maven project: its own `pom.xml`, `.gitignore`,
`README.md`, `FUTURE.md`, `config.properties.example`, `src/`, and tests.
Nothing in one directory imports from the other.

Once both are extracted, this repository becomes an archive.

---

## Step 1 — Create the two GitHub repositories

On GitHub, create two **empty** repositories (no README, no license, no
.gitignore — we bring our own):

- `Grantha2/conductor`
- `Grantha2/cowork-suite`

Set the default branch name to `main` in each.

## Step 2 — Extract each project with a clean history

The goal is a fresh launch pad, so start each repo from a single commit.
Run from your machine, in a scratch folder:

```bash
# --- conductor ---
git clone --branch claude/refactor-reduce-complexity-wKV0q --depth 1 \
    https://github.com/Grantha2/aicollab.git aicollab-src
mkdir conductor && cp -r aicollab-src/conductor/. conductor/
cd conductor
git init -b main
git add -A
git commit -m "Initial import: Conductor (from aicollab split)"
git remote add origin https://github.com/Grantha2/conductor.git
git push -u origin main
cd ..

# --- cowork-suite ---
mkdir cowork-suite && cp -r aicollab-src/cowork-suite/. cowork-suite/
cd cowork-suite
git init -b main
git add -A
git commit -m "Initial import: Cowork Suite (from aicollab split)"
git remote add origin https://github.com/Grantha2/cowork-suite.git
git push -u origin main
cd ..
```

Then in each new repo: copy `config.properties.example` → `config.properties`,
fill in your keys, and run `mvn compile exec:java`. Confirm both launch
before doing anything else.

> Prefer to keep full history for a directory? Use
> `git subtree split --prefix=conductor -b conductor-only` on a full clone
> and push that branch. Not recommended — the old history is noisy and
> `88b926c` on `claude/experimental-tools-mcp-room-workflow` remains the
> archival reference for anyone who needs it.

## Step 3 — Protect `main` in both new repos

Each project ships a `.github/workflows/ci.yml` that compiles and runs the
tests on every push and pull request. It is inert inside this repo and
starts running automatically once the directory is its own repository.

GitHub → Settings → Branches → Add rule for `main`:
- Require a pull request before merging
- Require the `CI / test` status check to pass
- Delete head branches automatically after merge

## Step 4 — Branch naming going forward

One branch per change, short-lived, deleted after merge:

| Prefix    | Use for                                | Example                        |
|-----------|----------------------------------------|--------------------------------|
| `feat/`   | New capability                         | `feat/openclaw-handoff`        |
| `fix/`    | Bug fix                                | `fix/gemini-role-mapping`      |
| `docs/`   | Documentation only                     | `docs/code-tour-refresh`       |
| `chore/`  | Build, deps, tooling                   | `chore/bump-gson-2-12`         |
| `spike/`  | Throwaway exploration; never merged    | `spike/streaming-ui`           |

Rules: lowercase, hyphens, ≤ 4 words after the prefix, say *what* not *who*.
No tool-generated names (`codex/new-task-9km3p0`), no personal names.

Commit messages: imperative, ≤ 72-char subject, blank line, then *why*.

## Step 5 — Clean up THIS repository (`aicollab`)

### 5a. Delete branches already merged into `main` (14)

These are fully contained in `main`; deleting loses nothing:

```bash
git push origin --delete \
  claude/elastic-goodall \
  claude/fix-blurry-gui-SOxl8 \
  claude/fix-gemini-api-HRASX \
  claude/gui-stakeholder-tab \
  claude/migrate-stateful-apis \
  codex/add-methods-to-promptbuilder.java \
  codex/add-swing-gui-and-profile-library-system \
  codex/add-swing-gui-and-profile-library-system-04yu9j \
  codex/add-swing-gui-and-profile-library-system-3dgkbr \
  codex/add-swing-gui-and-profile-library-system-i7nduj \
  codex/modify-orchestrator-constructor-to-accept-maxtokens \
  codex/new-task-9km3p0 \
  codex/plan-new-features-for-llm-integration \
  codex/plan-new-features-for-llm-integration-iusqmo
```

### 5b. Tag-then-delete branches with ideas worth keeping (5)

These never merged but contain prototypes referenced in the two
`FUTURE.md` files. A tag keeps the commits reachable forever; the branch
name stops cluttering the list.

```bash
git fetch origin
git tag archive/cloud-context-lambda origin/claude/generate-commit-message-Jn06O
git tag archive/rbac                 origin/claude/role-based-access-control-NnYLh
git tag archive/context-auditing     origin/claude/add-context-auditing-VHw0p
git tag archive/sessionstore-tests   origin/codex/new-task-to6z2l
git tag archive/full-suite-final     origin/claude/experimental-tools-mcp-room-workflow
git push origin --tags
git push origin --delete \
  claude/generate-commit-message-Jn06O \
  claude/role-based-access-control-NnYLh \
  claude/add-context-auditing-VHw0p \
  codex/new-task-to6z2l \
  claude/experimental-tools-mcp-room-workflow
```

### 5c. Delete superseded branches (6)

Their content was either merged via another branch or is an ancestor of
`archive/full-suite-final`:

```bash
git push origin --delete \
  claude/codex-review-fixes \
  claude/review-codebase-tmJfz \
  claude/gui-prompt-builder-controls-CYWUp \
  claude/gemini-server-side-state-1L9Ma \
  claude/gemini-server-side-state-yyLh8 \
  codex/new-task
```

### 5d. Finish

After both new repos build and you have pushed the tags:

1. Merge `claude/refactor-reduce-complexity-wKV0q` into `main` (so `main`
   shows this README + MIGRATION and the two directories).
2. Delete `claude/refactor-reduce-complexity-wKV0q`.
3. GitHub → Settings → **Archive this repository**.

## Step 6 — Secrets check (do this once, now)

`config.properties` was always gitignored, but verify no key ever slipped
into history before archiving:

```bash
git log --all -p -S 'sk-ant-' --oneline | head
git log --all -p -S 'AIza'    --oneline | head
git log --all -p -S 'sk-proj-' --oneline | head
```

If anything prints, **rotate that key at the provider** — deleting the
commit does not un-leak it.
