# ADR-045: The native round trip is measured by the native smoke test

**Date:** 15/10/2026

**Status:** Superseded by ADR-164

**Kind:** Design

**Context and constraints:** AT-39 asks for a native command round trip with a p95 under 50 ms over 200 calls.
The invariant catalogue runs on the JVM in `edi-core`'s tests, before the native executables exist (ADR-034), and only the `native` profile builds them, in `edi-cli`'s integration tests.

**Decision:** AT-39 is a real test in `NativeSmokeIT`, like AT-44: it calls `edi backlog`, an authenticated read of the store rendered as text, 200 times against the native core, writes p50, p95 and the slowest call to the build summary, and fails when p95 reaches 50 ms.
Its entry leaves the catalogue, which keeps only the acceptance tests that run on the JVM.
The smoke test also drives the S1 commands against the native core: adding and showing a task, raising and answering a decision, the migration dry run, a refusal, and `edi publish-check --json` reporting a finding when gitleaks is installed.

**Motivation:** The budget is a property of the native executables, so only a test of the native executables can prove it, and CI runs that test on every push.

**Alternatives considered:**
- Keeping AT-39 in the catalogue and skipping it on the JVM - it would never run where it can pass.
- Measuring `edi health` - it touches neither the token nor the store.

**Consequences:**
- Good: a change that slows the command or the core's reads fails CI.
- Bad: the budget is measured on CI runners as well as on the laptop the specification names, so a much slower runner could fail it.

**Revisit when:** CI runners make the measurement noisy.

**Sources:** EDI rewrite specification, sections 5.5 and 17.4 (AT-39, AT-44); ADR-005, ADR-034; `edi-cli/src/test/java/io/github/ythibos/edi/cli/NativeSmokeIT.java`.
