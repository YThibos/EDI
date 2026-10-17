# ADR-103: The tracker's token is read at each call and hidden from every message

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Adapters read credentials from the configured keys file at the moment of the call, and no log, event, report or error carries a secret (I-32, AT-32); the keys file is read only by the adapter, never by a model (section 14.2).

**Decision:** `JiraCalls` reads the token from the keys file's section through `KeysFile` for every call, sends it only in the `Authorization` header, and keeps it nowhere else; a refusal or failure names the call, the HTTP status and the tracker's own reasons with every occurrence of the token replaced by `[secret]`, and an unreadable keys file is named by its path only.
AT-32 runs every tracker call of intake, the answer, the sprint, the link and the transition against a stub tracker that demands a canary token, makes the stub echo the canary in refusals and failures, and finds it in no log line, no event, no wake, no answer and in neither the store's files nor the fleet ledger.

**Motivation:** A renewed token takes effect at the next call, and a tracker that echoes what it was sent cannot leak the token into EDI's records.

**Alternatives considered:**
- Reading the token once at start - a renewed token would need a restart, and the token would live in memory for the core's life.
- Dropping the tracker's reasons from every message - the captain would lose the one sentence that says what JIRA refused.

**Consequences:**
- Good: the keys file can change while the core runs.
- Bad: every call reads and parses the small keys file.

**Revisit when:** The keys file grows large, or a tracker needs a token that expires within a call sequence.

**Sources:** EDI rewrite specification, sections 14.2 and 16 (I-32, AT-32); ADR-093; `adapter.tracker.JiraCalls`, `KeysFile`, `TrackerUnavailable`, `adapter.home.TrackerSettings`, `invariant.ProcessSafetyTest`, `testing.StubJiraServer`, `testing.CapturedLogs`.
