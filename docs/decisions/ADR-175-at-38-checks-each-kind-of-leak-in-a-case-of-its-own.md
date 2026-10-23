# ADR-175: AT-38 checks each kind of leak in a case of its own, and each case starts its push before EDI delivers

**Date:** 23/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** AT-38 proves that a branch with a secret, one with a deny-listed term and one with a personal path are refused at `edi deliver` and at the worker's push, and that a clean branch passes both (I-38, ADR-032).
ADR-145 kept the four branches in one test, started the four worker pushes together and delivered each branch while they ran.
That one test then did four dispatches, started four guard JVMs and ran gitleaks eight times, within one 30-second timeout (AGENTS.md, ADR-065).
On 23/10/2026, with a load average of 30 to 39, the test JVM of a full `./mvnw verify` had about 8% of the machine, so a test's time followed the processor work it and its child processes asked for.
With the store's waits for the disk gone (ADR-174), the test still overran the 30 seconds in that run, while every other AT-38 test, which pushes at most one branch, took 13.3 seconds or less.

**Decision:** `deliveryAndPushRefuseEachKindOfLeak` is a parameterised test over `TwoMachinesTest.Leak`, a secret, a deny-listed term and a personal path, and each case builds its own home, dispatches one branch, starts its push, delivers while the push runs and then judges both.
Each case asserts that the delivery is refused without naming the secret, that the push is refused without naming the secret or the deny-listed host, and that no draft was opened.
`deliveryAndPushPassAClearBranch` delivers and pushes the clean branch the same way, and asserts that exactly one draft was opened and the push passed.
`PrePushHook.starts` and `RunningHook.finished` stay as ADR-145 made them, and the stand-in `edi` still starts the guard with `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC`.

**Motivation:** Measured at a load average of about 41, each case took 5 to 14 seconds, as the other AT-38 tests do, where the one test had taken 30 seconds at a load of 30 to 39.
Every kind of leak is still refused at both places, every push still runs the real hook and a guard in a process of its own, and the timeout stays at 30 seconds.
ADR-034 keeps one test method per acceptance test, and the parameterised method stays one, as `aSecretIsStillRefusedFromEitherProject` does.

**Alternatives considered:**
- Raising the timeout - it hides the cost, and the test would keep growing with every kind of leak it checks.
- Keeping the one test and starting a lighter guard - the guard's JVM starts in about 0.4 seconds alone, so most of the test's work is gitleaks and git, which the check needs.
- A class-data-sharing archive for the guard's JVM - the JVM refuses to archive classes loaded from directories, and the tests run from `target/classes` (ADR-145).

**Consequences:**
- Good: each case has the whole timeout for one branch, and a failing case names the kind of leak it checks.
- Bad: each case pays for its own home and clone of the project, so the class does a little more work than before.

**Revisit when:** `edi-core`'s tests can run the native `edi` against a core, or AT-38 gains a check that needs several branches in one home.

**Sources:** EDI rewrite specification, section 4.8 (I-38, AT-38); ADR-032, ADR-034, ADR-065, ADR-145, ADR-174; `edi-core` `invariant.TwoMachinesTest`, `testing.PrePushHook`, `testing.TestProcesses`, `application.publish.PublishGuard`.
