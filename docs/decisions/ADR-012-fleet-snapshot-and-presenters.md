# ADR-012: One fleet snapshot query, many presenters

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The old harness had two overview commands, a text report and a board, each assembling the fleet state its own way, and passing that state through command-line arguments failed once the backlog grew past 133 KB.
The captain asked whether the overview logic would have a reuse case "(Clean Architecture things...)".

**Decision:** The fleet state is one query, `FleetSnapshotQuery`, computed from the store in one read transaction and never parsed from prose, reports or panes.
Every surface that shows the fleet is a separate presenter over its `FleetSnapshot`: the boxed captain report, the console's sitrep page, the wake digest of `edi wait`, the session-start digest, and later the work board.
Only the primary knows what it did in a turn, so it passes that as a small JSON document on standard input to `edi report`, which merges it with the snapshot.

**Motivation:** One query keeps every surface consistent; separate presenters keep each surface's format independently testable, and large data travels through standard input and HTTP bodies, never arguments (I-36).

**Alternatives considered:**
- A separate overview command next to the report - duplicated state assembly, the old harness's arrangement.

**Consequences:**
- Good: a new surface is a new presenter, not a new query.
- Bad: the snapshot read model must carry everything any presenter needs.

**Revisit when:** A presenter needs data the snapshot cannot carry cheaply.

**Sources:** EDI rewrite specification, sections 5.4 and 15 (decision L1, I-36).
