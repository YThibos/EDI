# ADR-067: The session the captain typed in last is the primary, and any other EDI session stands by

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-02: a session in which the captain types a prompt claims the primary role; any other primary becomes standby and read-only.
AT-02: in two simulated sessions, a typed prompt in the second moves the role, and the first's mutating `edi` calls are refused with the standby reason.
Not every prompt is the captain's: when a background job ends, Claude Code 2.1.301 submits a `<task-notification>` prompt that fires `UserPromptSubmit`, and EDI's own injected lines carry the operational marker.
A primary that crashed sends no `SessionEnd`.

**Decision:** `primary_session` keeps every session that runs EDI's primary hooks: its Claude process (pid and start time from the process table), when it claimed the role, its current turn and its typed prompts' digests in `typed_prompt`.
`PrimaryRole` gives the role to the session that claimed it last, among the sessions that have not ended and whose Claude process still runs, or whose process is unknown.
A session claims the role at its start when no live session holds it, and at every prompt `Prompt.isTypedByTheCaptain` accepts, which is any prompt that is neither a background job's notice nor marked by `OperationalText`; a claim takes the role from every other session in the same transaction.
`ApiTokenFilter` turns the home token with an `EDI-Session` header into `Caller.Standby` when another session holds the role, and `Caller.requirePrimary` refuses a standby caller with one sentence saying why and how the session becomes primary again.
A call without a session header, such as the captain's shell, is a primary caller, because nothing contends with it.
`SessionEnd` releases the role; a crashed holder loses it as soon as its process is gone.

**Motivation:** Two sessions can never both change the fleet, and the role moves only on the captain's own typing, never on a notice that arrived while he was elsewhere.

**Alternatives considered:**
- A lock file held by the primary - the old harness's fleet lock, which outlived crashed sessions and needed takeover rules.
- Claiming on every prompt - a background job's notice would hand the role to an idle session.

**Consequences:**
- Good: a session restarted after a crash takes the role at once, because its predecessor's process is gone.
- Bad: a session whose Claude process EDI could not identify is assumed live until it ends or another session claims.

**Revisit when:** The console's captain session arrives with slice S7, or slice S8 uses the typed prompts as captain-word evidence.

**Sources:** EDI rewrite specification, sections 9.1, 10.2 and I-02; `domain.primary.PrimarySession`, `PrimaryRole`, `Prompt`, `Standing`, `application.primary.PrimarySessions`, `PrimaryHooks`, `web.access.Caller`, `ApiTokenFilter`, `adapter.store.PrimarySessionStore`, `adapter.process.ProcessTable`.
