# ADR-023: Merges are observed, never performed

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** After the captain merges a draft, EDI must refresh the project clone before briefing follow-up work, transition tickets and clean up (I-29).
A merge done on the forge's website leaves the local clone stale, which caused follow-up work on outdated code in the old harness.

**Decision:** EDI checks each recorded open draft every 10 minutes through the forge's command-line tool, counting only an exact `merged` state, and still accepts the captain's word (`edi merged <id|url>`).
A seen merge refreshes the clone and verifies the merge commit before any follow-up in that repository is briefed.

**Motivation:** Merges are noticed without the captain having to say so, and his word still works immediately.

**Alternatives considered:**
- The captain's word only - forgotten merges leave clones stale.
- Forge webhooks - would need a service reachable from the forge.

**Consequences:**
- Good: follow-up work never starts on a stale clone.
- Bad: up to 10 minutes of latency when the captain says nothing.

**Revisit when:** The forges offer a cheap local notification.

**Sources:** EDI rewrite specification, sections 12.1, 13.1 and 13.2 (decision E14, I-29); the method-name rule `NameAMergeOperation` keeps observation and merging apart.
