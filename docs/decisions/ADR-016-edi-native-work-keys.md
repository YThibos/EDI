# ADR-016: EDI-native work keys, with `EDI-1` for upkeep

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Branches carry a work key and every commit subject starts with it (I-08, I-10).
On the work laptop the key is a JIRA ticket; the private desktop has no tracker.
The captain: "Set up the basics. I might want project keys for specific things in my own development."

**Decision:** A project without a tracker gets EDI-native keys `<KEY>-<n>` from a per-project sequence in the store, configured in one line per project (`edi project add <name> ... --key <KEY>`); changing the prefix affects future tasks only.
EDI's own repository uses `EDI-<n>` keys.
`EDI-1` is the evergreen upkeep key, "Dependencies and tooling upkeep": Renovate's commit subjects and pull request titles start with it, so bot commits obey I-10 too.
Slices S0 to S11 take `EDI-2` to `EDI-13`, and every further EDI task takes the next number; EDI's own sequence is seeded with the highest key used when it migrates the old harness's backlog.
Laptop time spent on EDI is booked on the tooling ticket through the time-booking tool's folder map, outside this repository.

**Motivation:** Branches and commits are keyed from day one on every machine, and a later EDI-native work board keeps the same keys.

**Alternatives considered:**
- No keys where no tracker exists - breaks I-08 and I-10 on the desktop.

**Consequences:**
- Good: one branch and commit convention everywhere: `feature|chore|research/<KEY>-<n>`.
- Bad: EDI owns a key sequence per project.

**Revisit when:** An EDI-native work board replaces the external tracker on a machine.

**Sources:** EDI rewrite specification, sections 5.7, 8.5, 14.4 and 20.1 (decisions E6, E7); `renovate.json` (`commitMessagePrefix`, `dependencyDashboardTitle`).
