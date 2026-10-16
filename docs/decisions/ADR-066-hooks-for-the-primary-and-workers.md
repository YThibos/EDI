# ADR-066: The primary and every worker answer all seven Claude Code hooks through edi hook

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 10.2 gives the primary and the workers seven hooks: `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `Stop`, `StopFailure`, `Notification` and `SessionEnd`.
Slice S3 answered four of them, and only for workers; the primary's hooks were guarded but recorded nothing.
A primary is told apart from a worker by its token (ADR-049), but every primary holds the home token, so two primary sessions need another name.
On Claude Code 2.1.301 every hook and every Bash command of a session carries `CLAUDE_CODE_SESSION_ID`, equal to the hook payload's `session_id`, and `CLAUDE_PID`, the session's own process ([docs/verification/claude-code.md](../verification/claude-code.md)).

**Decision:** `claude/worker/settings.json` and the new `claude/primary/settings.json` define all seven hooks, each running `edi hook <event>`; the primary's guard matches Bash only, because the primary writes no project files.
`edi` sends `CLAUDE_CODE_SESSION_ID` as the `EDI-Session` header and `CLAUDE_PID` as `EDI-Claude-Pid` on every call, so the core knows which session a hook or a command comes from.
`HookController` sends a worker's hooks to `WorkerHooks` and the home token's hooks to `PrimaryHooks`:

| Hook | Primary | Worker |
|---|---|---|
| `SessionStart` | registers the session with its Claude process and answers the session-start digest (ADR-076) | records the session id Claude Code reports and its transcript path |
| `UserPromptSubmit` | starts a turn; a prompt the captain typed claims the primary role (ADR-067) | marks the worker busy and receives a typed steer (ADR-056) |
| `PreToolUse` | the guard (ADR-058) | the guard with write containment |
| `Stop` | the turn-end rules (ADR-068) | marks the worker idle, or delivers a waiting message |
| `StopFailure` | records a usage limit the session stopped on | parks the worker on its limit, or records the error (ADR-072) |
| `Notification` | records the latest notification on the session | records a `WorkerNotified` event |
| `SessionEnd` | releases the primary role | marks the session ended and counts a recovery's tokens (ADR-075) |

The primary's guard needs no session id, so it still refuses a tool call when the payload names none.

**Motivation:** Both kinds of session are observed through the same structured events, so supervision never reads a pane or a transcript to learn what a hook already says.

**Alternatives considered:**
- Writing the session id into `CLAUDE_ENV_FILE` at session start - it works only after the first hook and adds a file per session; the variable Claude Code already sets is exact.
- One token per primary session - a second secret per session, while the role, not the secret, is what two primaries contend for.

**Consequences:**
- Good: the timeline of a worker now holds its notifications, stop failures and session end.
- Bad: a primary session started without `claude/primary/settings.json` is not registered; it becomes known at its first typed prompt, which registers it.

**Revisit when:** Slice S8 starts the primary through `edi up` with these settings, or Claude Code renames a hook or a variable EDI reads.

**Sources:** EDI rewrite specification, sections 10.1 and 10.2 (decision E4); ADR-049, ADR-056, ADR-058; `claude/primary/settings.json`, `claude/worker/settings.json`, `web.hook.HookController`, `HookEvent`, `application.primary.PrimaryHooks`, `application.worker.WorkerHooks`, `edi-cli` `ClaudeSession`, `CoreClient`.
