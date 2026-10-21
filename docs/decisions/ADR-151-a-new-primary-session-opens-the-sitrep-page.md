# ADR-151: A new primary session opens the console's sitrep page in the captain's browser

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain asked for the sitrep page to open by itself at every new EDI session, signed in, as `edi console` opens it.
`edi up` launches a new Claude Code session for the primary, and the primary's `SessionStart` hook reaches the core at every start of a session: Claude Code names the start's `source` as `startup` for a new launch, and as `resume`, `clear` or `compact` otherwise.
A standby session and a worker also send `SessionStart`, and the page must not open again on every turn or every compaction.
The browser is opened through `CaptainsBrowser` (ADR-150), which can fail, and a failing hook would cost the session its digest (ADR-132).
Machine differences come from the machine profile at runtime (ADR-006).

**Decision:** `PrimaryHooks.sessionStarted` answers a `SessionStart`: whether Claude Code launched the session (`source` is `startup`), whether the store had never seen the session before, and the session's standing once the start is recorded.
`SessionStart.opensTheSitrep` holds only for the launch of a new session that holds the primary role, and `OpenTheSitrepAtSessionStart` then opens the console through `OpenConsole` (ADR-105), unless the machine profile sets `console.openSitrepAtSessionStart: false`; the setting is on when absent.
A worker's `SessionStart` never reaches this rule.
The session-start digest tells the primary the outcome in one line after the presence: the address the page is open at, or, when the browser could not open it, the console's address and that `edi console` opens it signed in.
The sign-in link itself never enters the digest.

**Motivation:** The store's record of the session and Claude Code's `source` together tell a new primary from the same primary resumed, compacted or cleared, so the page opens once per new primary session however Claude Code numbers its sessions.
Telling the primary where the page is, or how to open it, keeps a failed opening from going unnoticed without failing the start.

**Alternatives considered:**
- Opening the page from `edi up` - a primary started in another way would not get it, and only the core's adapters start programs (ADR-009).
- Opening the page from the anchor's instructions - nothing guarantees the primary would run `edi console`, and the hook does.
- Opening it after `/clear` too - a cleared conversation is the same captain at the same desk, and the page is already open.
- Off by default - the captain asked for it on every new session.

**Consequences:**
- Good: the captain finds the sitrep page in the browser as soon as a new primary session starts.
- Good: another machine or captain switches it off in the machine profile.
- Bad: the session start waits for the browser, at most 15 seconds.
- Bad: the sitrep line is not rebuilt from the store, so the digest of a resumed session leaves it out.

**Revisit when:** Claude Code changes the sources it names at session start, or the captain wants the page at standby sessions too.

**Sources:** The captain's request of 21/10/2026; ADR-006, ADR-009, ADR-067, ADR-105, ADR-132, ADR-150; `domain.primary.SessionStart`, `application.primary.PrimaryHooks`, `OpenTheSitrepAtSessionStart`, `SessionStartDigest`, `adapter.home.MachineProfile`, `web.hook.HookController`, `web.console.ConsoleAddress`, `web.primary.SessionStartDigestText`.
