# ADR-076: A session starts from a digest rebuilt from the store

**Date:** 16/10/2026

**Status:** Superseded by ADR-132

**Kind:** Design

**Context and constraints:** I-23: a restart is a non-event; all state lives in the store and git, and after a restart the primary rebuilds its picture from the store, not from memory; AT-23: after the core and the primary are killed mid-task and restarted, the session-start digest equals the pre-crash snapshot.
Section 12.5: when the primary hits a usage limit, the next session's digest says so and lists the parked work.
Section 5.4 makes the session-start digest a presenter over the fleet snapshot; slice S8 adds the memory and catch-up parts.
Claude Code adds a `SessionStart` hook's `additionalContext` to the session's context.

**Decision:** `SessionStartDigestQuery` reads from the store and from tmux the session's standing, the limit stop of the primary session before it, the fleet snapshot, every serving worker with its health, the open outage hold list and the number of unacknowledged wakes; `SessionStartDigestText` writes it as plain lines, and the primary's `SessionStart` hook answers it as `additionalContext`.
The previous primary's limit stop is told only to the first session that starts after it.
The digest ends by asking for one background `edi wait` while work is in flight, except in a standby session.

**Motivation:** Everything the digest says comes from the store and from tmux, so a restarted core and a new primary see exactly the picture the old ones had.

**Alternatives considered:**
- The captain report as the digest - it is shaped for the captain and leaves out worker health and the outage list.
- Letting the primary run `edi sitrep` at its start - nothing guarantees it would, and the hook does.

**Consequences:**
- Good: AT-23 compares the whole digest text before the crash and after the restart.
- Bad: the digest grows with the fleet; it is plain text without the captain report's width limit.

**Revisit when:** Slice S8 adds memory, presence and the catch-up to the session start.

**Sources:** EDI rewrite specification, sections 5.4, 10.2, 12.5 and I-23; ADR-012; `application.primary.SessionStartDigestQuery`, `SessionStartDigest`, `web.primary.SessionStartDigestText`, `web.hook.HookController`.
