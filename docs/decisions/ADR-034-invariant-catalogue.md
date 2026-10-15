# ADR-034: The invariant catalogue reports pending acceptance tests

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** EDI's specification states 39 invariants, the captain's way of working, each with one or more acceptance tests, plus acceptance tests for presence and for the build itself.
Slice S0 creates them all as named tests that later slices make pass, and an invariant without a passing test blocks the cutover from the old harness.
`./mvnw verify` must stay green while most of them cannot pass yet.

**Decision:** The catalogue lives in `edi-core`'s test package `invariant`, one class per group of invariants, one nested class per invariant and one test method per acceptance test, each named after its AT id and statement.
A test whose behaviour does not exist yet calls `Pending.until(Slice.Sn)`, which aborts it with "pending until slice Sn ...", so JUnit reports it as skipped and the build stays green.
A test whose subject may already exist states its real check and skips by assumption while the subject is absent; the size check of the primary anchor (AT-35, at most 10,000 bytes with its personal addendum) is armed that way and runs as soon as `claude/primary/CLAUDE.md` exists.
CI writes the count of pending tests per slice to the build summary (`ops/catalogue-summary.sh`).
A slice makes an acceptance test pass by replacing its `Pending.until` call with the real test.
The acceptance tests that slice S0 delivers outright are real tests elsewhere: AT-44 is the native smoke test (`NativeSmokeIT`), AT-45 the port inventory (`PortInventoryTest`), and the guard half of AT-38 is `PublishCheckTest`.

**Motivation:** The catalogue is visible on every build, in the specification's own structure, and progress is the falling count of pending tests.

**Alternatives considered:**
- Disabled tests - reported the same way, but invisible as work still owed.
- A separate profile in which the catalogue fails - `verify` would no longer show it at all.

**Consequences:**
- Good: the cutover check is "no pending test left".
- Bad: a pending test proves nothing about the behaviour until its slice writes it.

**Revisit when:** The catalogue is empty.

**Sources:** EDI rewrite specification, sections 4, 12.7, 17.4 and 19 (slice S0); `edi-core/src/test/java/io/github/ythibos/edi/invariant/`, `ops/catalogue-summary.sh`.
