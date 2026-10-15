# ADR-005: Native executables for the command and the installed core

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain: "I want a native Graal image for fast startup."
Claude Code hooks call the command on every turn, so its start-up time is paid constantly.
A native compile takes minutes, which would destroy a local development cycle measured in seconds.

**Decision:** The `edi` command is always a GraalVM native executable; the core is installed as one too.
Development and tests run on the JVM.
The Maven profile `native` compiles both executables and then runs the native smoke test (`NativeSmokeIT`, AT-44): the native core starts on a fresh home, creates its store, answers `/api/health` and serves the console, and the native command passes its contract against it.
CI runs the profile on every push, and every install runs it first.
If a native problem ever blocks a release, the core may run on the JVM until it is fixed; the command never falls back.

**Motivation:** A native command starts in milliseconds, so hooks cost nothing noticeable, and a native core starts in well under a second with little memory.
Keeping tests on the JVM keeps `./mvnw verify` within minutes.

**Alternatives considered:**
- JVM everywhere - every hook would pay a JVM start-up.
- Native everywhere, including tests - minutes per test run.

**Consequences:**
- Good: the smoke test catches native-only failures; on 15/10/2026 it caught a `@Value` on a record component that Spring's ahead-of-time code tried to inject into the record's final field, and an application class that Spring proxied with generated code.
- Bad: ahead-of-time processing fixes the bean graph at build time, which constrains how machine differences are chosen (ADR-006) and forbids runtime class loading (ADR-009).
- Bad: the native job is the slowest in CI.

**Revisit when:** A JVM feature such as the ahead-of-time class cache makes JVM start-up fast enough for hooks.

**Sources:** EDI rewrite specification, sections 5.5 and 17.2 (decision E17); `edi-core/pom.xml` and `edi-cli/pom.xml` (profile `native`), `edi-cli/src/test/java/io/github/ythibos/edi/cli/NativeSmokeIT.java`, `.github/workflows/ci.yml` (job `native`).
