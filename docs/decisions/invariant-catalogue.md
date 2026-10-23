# The invariant catalogue holds every acceptance test, and a pending one is reported as skipped

**Kind:** Design

## Context

EDI's specification states 39 invariants of how the captain works, each with acceptance tests, plus acceptance tests for presence and the build.
Slice S0 created them all as named tests that later slices make pass, and an invariant without a passing test blocked the cutover from the old harness.
`./mvnw verify` had to stay green while most of them could not pass yet.

## Decision

The catalogue lives in `edi-core`'s test package `invariant`, one class per group of invariants, one nested class per invariant and one test method per acceptance test, each named after its AT id and statement.
A test whose behaviour does not exist yet calls `Pending.until(Slice.Sn)`, which aborts it so JUnit reports it as skipped; a slice makes it pass by replacing that call with the real test.
A test whose subject may already exist states its real check and skips by assumption while the subject is absent.
Acceptance tests that need the native executables are real tests in `edi-cli` instead, such as AT-39 and AT-44 ([native-smoke-test](native-smoke-test.md)), as are AT-45 (`PortInventoryTest`) and the guard half of AT-38 (`PublishCheckTest`).
CI writes the count of pending tests per slice to the build summary (`ops/catalogue-summary.sh`).

## Why

The catalogue is visible on every build in the specification's own structure, and progress is the falling count of pending tests.

## Alternatives rejected

- Disabled tests - reported the same way, but invisible as work still owed.
- A profile in which the catalogue fails - `verify` would no longer show it at all.

## Consequences

- Good: the cutover check was "no pending test left".
- Bad: a pending test proves nothing until its slice writes it.

## When to revisit

When the catalogue holds no pending test and a new specification wants a different structure.

## How we got here

- 15/10/2026: slice S0 created the whole catalogue as pending tests, and slices S1 to S11 made them pass.
