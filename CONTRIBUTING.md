# Contributing to Chloe (OpenClaw EE)

## Golden Rule: Everything Starts with an Issue

Before any code is written, there must be a GitHub Issue describing the work.
No issue → no branch → no PR. This is non-negotiable.

- **Bugs:** describe what is happening, what was expected, and reproduction steps.
- **Features:** describe the goal and acceptance criteria.
- **Chores/refactors:** describe what and why.

PRs that don't reference an issue will not be merged.

---

## Workflow

1. **Open or identify an issue** for the work to be done
2. **Cut a branch** from latest `main` following the naming convention below
3. **Implement** the change
4. **Tests must pass** — run `./gradlew test` locally before opening a PR
5. **Build must pass** — run `./gradlew assembleDebug` locally before opening a PR
6. **Open a PR** following the PR format below
7. **CI must be green** — Actions runs tests and build on every PR; fix failures before requesting review
8. **Request review** — flag the PR as ready; Paolo reviews and merges
9. **Nothing is pushed directly to `main`** — ever

---

## Branch Naming

```
<type>/<short-description-in-kebab-case>
```

| Type | When to use |
|------|-------------|
| `feat/` | New feature or capability |
| `fix/` | Bug fix |
| `test/` | Tests only, no production code changes |
| `ci/` | CI/CD and build configuration |
| `refactor/` | Code restructure with no behaviour change |
| `chore/` | Dependencies, config, maintenance |
| `docs/` | Documentation only |

**Examples:**
```
feat/settings-screen
fix/silence-detector-reset
test/kokoro-edge-cases
ci/gradle-cache
refactor/voice-pipeline-interface
```

---

## Commit Messages

Every commit must have a subject line and a body. No exceptions.

```
<type>: <short summary in imperative mood, max 72 chars>

<Body — always required. Describe what was added, changed, or removed.
Include any notable decisions or tradeoffs. Write enough detail that
someone can understand the change without reading the diff.>
```

**Types:** `feat`, `fix`, `test`, `ci`, `refactor`, `chore`, `docs`

**Example:**
```
feat: add SilenceDetector with configurable threshold

Added SilenceDetector class that tracks RMS amplitude over time and
fires a callback when silence exceeds a configurable duration (default
1000ms). Uses an injected time provider for testability. Integrated
into AudioRecorder as a constructor parameter so it can be mocked in
tests. The 150f RMS threshold was chosen to ignore ambient noise on
the Pixel 9 without requiring calibration.
```

---

## Pull Request Format

**Title:** same as the lead commit subject — `<type>: <short summary>`

**Labels:** use labels to indicate the scope of the change (e.g. `audio`, `stt`, `tts`, `llm`, `pipeline`, `ui`, `settings`, `models`, `ci`, `deps`)

**Description template:**

```markdown
## Issue
Closes #<number>

## What was implemented
<How the feature or fix was built. Describe the approach, architecture
decisions, and anything notable that isn't obvious from the code.>

## Tests added
- <Plain English description of what behaviour is now verified>
- <Another test scenario, written for a human not an IDE>
- <etc.>
```

**Example:**

```markdown
## Issue
Closes #7

## What was implemented
Added SilenceDetector as a standalone class injected into AudioRecorder.
The detector tracks RMS amplitude from the recording loop and fires an
onSilenceDetected callback when silence exceeds the configured threshold
(default 1 second). An injectable time provider replaces SystemClock
calls to make the class fully testable without Android dependencies.
VoicePipeline now wires the silence callback to automatically trigger
the STT pipeline, removing the need for a manual stop.

## Tests added
- Recording stops automatically after 1 second of silence
- Silence detector resets correctly when speech resumes mid-silence
- Custom silence thresholds (500ms, 2000ms) are respected
- Pipeline triggers transcription automatically on silence, without manual stop
- Ambient noise below the RMS threshold does not trigger silence detection
```

---

## Project Board

The Chloe project is tracked on the [GitHub Project board](https://github.com/orgs/openclaw-ee/projects/1).

**Track issues only — never add PRs to the board directly.**

PRs are linked to their issue via `Closes #<number>` in the PR description. When you open an issue card on the board, the linked PR is visible in the "Linked pull requests" field. There is no need to add PRs as separate board items — doing so creates clutter and confusion.

### Statuses

| Status | Meaning |
|--------|---------|
| 📋 Roadmap | Captured for the future, not yet prioritised |
| 🗂 Backlog | Prioritised and ready to be picked up, but may need refinement |
| 🔍 Ready | Fully defined, acceptance criteria clear — can start immediately |
| 🚧 In Progress | Being actively worked on |
| 👀 In Review | PR open, waiting for review |
| ✅ Done | Merged to main |

Work is only picked up from **🔍 Ready**. If something is in Backlog and needs to be started, refine and move it to Ready first.

Only one or two issues should be **🚧 In Progress** at a time.

### Who manages the board

Devon (the AI dev manager) is responsible for:
- Adding new issues to the board when created
- Moving issues through statuses as work progresses (Backlog → Ready → In Progress → In Review)
- Removing any PRs that accidentally get added

Paolo moves issues to **✅ Done** by merging the associated PR.

## Labels

Apply at least one scope label to every PR:

| Label | Scope |
|-------|-------|
| `audio` | Microphone capture, AudioRecorder, WAV handling |
| `stt` | Speech-to-text, Whisper integration |
| `tts` | Text-to-speech, Kokoro integration |
| `llm` | LLM processing, API client, routing |
| `pipeline` | VoicePipeline orchestration, state machine |
| `ui` | Activities, layouts, ViewModels, animations |
| `settings` | User preferences, SharedPreferences |
| `models` | Model files, ModelManager, download scripts |
| `ci` | GitHub Actions, build config |
| `deps` | Dependency updates |
