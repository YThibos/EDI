# ADR-145: AT-38 starts the worker's pushes before EDI delivers, and the hook's guard runs on a quick-starting JVM

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** AT-38 proves that a branch with a secret, one with a deny-listed term and one with a personal path are refused at `edi deliver` and at the worker's push, and that a clean branch passes both (I-38, ADR-032).
The push runs the real `pre-push` hook (ADR-053), whose `edi` is a stand-in that starts a JVM running `PublishGuard`, because `edi-core`'s tests have neither the native `edi` nor a running core.
Each of those JVMs loads about a thousand library classes, most of them Jackson's, and its optimising compiler works on them, although the guard runs one check and exits.
The four pushes ran one after another, each after its delivery, and took about half of the test; in the full parallel suite the test overran the 30-second timeout every test must keep (AGENTS.md, ADR-065).

**Decision:** `PrePushHook.starts` starts the hook and returns a `RunningHook`, whose `finished` waits for the hook and returns its `HookRun`; `PrePushHook.pushes` does both in a row.
AT-38 starts the four worker pushes once every branch is committed, delivers each branch while they run, and then judges each push, so the pushes overlap the deliveries instead of following them.
`PrePushHook` installs the stand-in once per EDI home, so no push rewrites it while another runs it.
The stand-in starts the guard with `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC`, because one short check needs neither the optimising compiler nor a parallel collector.
Every leak kind is still refused at both places, every push still runs the real hook and a guard in a process of its own, and the timeout stays at 30 seconds.

**Motivation:** The test does the same work in less time: alone it took about 12 seconds, of which the four pushes took about 5, and now takes 6 to 7, together with ADR-146.
In the full suite the pushes ran one after another for about 18 seconds; now they run while EDI delivers, and what is left of the suite's load is ADR-149's concern.

**Alternatives considered:**
- Raising the timeout - it hides the cost and keeps the build slow, and nothing in the test needs more time once the pushes overlap.
- One test per leak kind - each case would pay for its own home and dispatch, and the catalogue keeps one test per acceptance test (ADR-034).
- A stand-in `edi` that posts to a publish check served by the test, as the native command posts to the core - no JVM per push, but it copies the core's web layer into test code and needs `curl`.
- A class-data-sharing archive for the guard's JVM - the JVM refuses to archive classes loaded from directories, and the tests run from `target/classes`.

**Consequences:**
- Good: the four guards run at the same time as the deliveries, so the pushes add about a second to the test alone.
- Bad: when an assertion on a delivery fails, `TestProcesses` stops the pushes still running and adds a failure for them (ADR-065).

**Revisit when:** `edi-core`'s tests can run the native `edi` against a core, or a test needs its pushes and deliveries in a fixed order.

**Sources:** EDI rewrite specification, section 4.8 (I-38, AT-38); ADR-032, ADR-034, ADR-053, ADR-065, ADR-146, ADR-149; `edi-core` `invariant.TwoMachinesTest`, `testing.PrePushHook`, `testing.TestProcesses`, `application.publish.PublishGuard`.
