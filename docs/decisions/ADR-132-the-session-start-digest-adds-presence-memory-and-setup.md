# ADR-132: A session starts from a digest rebuilt from the store, with the address word, presence, memory, setup and the weekly upkeep

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-23: a restart is a non-event; after a restart the primary rebuilds its picture from the store, not from memory; AT-23: after the core and the primary are killed mid-task and restarted, the session-start digest equals the pre-crash snapshot.
Section 12.5: when the primary hits a usage limit, the next session's digest says so and lists the parked work.
Section 5.4 makes the session-start digest a presenter over the fleet snapshot; section 5.7: the core suggests the upkeep check weekly in the session-start catch-up; section 20.6: the first digest on a new machine lists anything still missing.
The anchor names no address word (ADR-131), and memory loads with the system prompt (ADR-121).
A hook's `additionalContext` arrives in full only up to 10,000 characters.

**Decision:** `SessionStartDigestQuery` reads from the store, the home and tmux: the session's standing, the address word from personal.yaml, the limit stop of the primary session before it, the fleet snapshot, every serving worker with its health, the open outage hold list, the number of unacknowledged wakes, the presence, the startup memory check, the files missing from the EDI home (`HomeSetup`) and whether the weekly upkeep is due; `SessionStartDigestText` writes it as plain lines, and the primary's `SessionStart` hook answers it as `additionalContext`.
The memory line says how the memory fits its budget and, when it passes, tells the primary to read the memory files only if they are not in its system prompt; when it fails it asks for `/stow`.
The weekly upkeep is suggested on Mondays (`WeeklyUpkeep`), to a session that is not standby.
The previous primary's limit stop is told only to the first session that starts after it, and the digest ends by asking for one background `edi wait` while work is in flight, except in a standby session.

**Motivation:** Everything the digest says comes from the store, the home and tmux, and a rule tied to the day rather than to the session keeps a restarted session's digest equal to the one before the crash.

**Alternatives considered:**
- Suggesting the upkeep in the week's first session - the session restarted after a crash would see a different digest.
- The captain report as the digest - it is shaped for the captain and leaves out worker health and the outage list.

**Consequences:**
- Good: AT-23 still compares the whole digest text before the crash and after the restart.
- Bad: the digest grows with the fleet, and above 10,000 characters it arrives as a preview.

**Revisit when:** The digest nears the limit on hook context.

**Sources:** EDI rewrite specification, sections 5.4, 5.7, 10.2, 12.5, 20.6 and I-23 (AT-23); ADR-012, ADR-076, ADR-120, ADR-121, ADR-122, ADR-131; `application.primary.SessionStartDigestQuery`, `SessionStartDigest`, `domain.primary.WeeklyUpkeep`, `adapter.home.HomeSetup`, `PersonalConfiguration`, `application.memory.MemoryQuery`, `application.presence.PresenceQuery`, `web.primary.SessionStartDigestText`, `web.hook.HookController`.
