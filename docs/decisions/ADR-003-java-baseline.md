# ADR-003: Java 25 until GraalVM Community supports a newer JDK

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain chose Java 27, the current release, to stay on the bleeding edge: "we will be bleeding edge anyway (JKD 27 since that's the latest now)".
The specification makes that choice conditional: install 27, verify that GraalVM Community Edition for JDK 27 and Spring Boot 4 support it, and fall back to Java 25 only if a native toolchain for 27 is missing, saying so.
The verification on 15/10/2026 found:
- JDK 27 reached general availability on 15/09/2026.
- GraalVM Community Edition publishes no build on JDK 27: its release calendar and `graalvm/graalvm-ce-builds` list only the JDK 25 line (the latest is "GraalVM Community 25 Innovation 4", JDK 25.0.4.1.1, 22/09/2026), with feature releases on JDK 25 planned into 2027.
- Spring Boot 4.1.1 declares compatibility with Java up to and including 26, not 27.
- mise offers `graalvm-community` builds up to 25.0.2.

**Decision:** EDI builds and runs on Java 25 with GraalVM Community Edition `graalvm-community-25.0.2`, pinned in `mise.toml`, and the Maven property `java.version` is 25.

**Motivation:** Native executables are a settled requirement (ADR-005), and the only open-source native toolchain available is on JDK 25.
Class files compiled for 27, and preview features in particular, cannot be compiled by a JDK 25 native-image builder, so mixing 27 for the JVM with 25 for native images is not possible.

**Alternatives considered:**
- Java 27 on the JVM and no native images - breaks ADR-005 and the command's start-up target.
- Oracle GraalVM innovation builds - not open source (ADR-029), and they are also on JDK 25.
- A JDK 27 core on the JVM with a JDK 25 native command - two JDKs in one build, and Spring Boot 4.1 does not support 27 yet.

**Consequences:**
- Good: the native build, the tests and the installed executables all use one JDK.
- Bad: EDI is two releases behind the newest JDK until GraalVM Community ships a newer base.
- Bad: the mise pin cannot follow GraalVM's newer innovation builds, which mise does not list; Renovate watches the `jdk-*` releases of GraalVM Community instead (ADR-027).

**Revisit when:** GraalVM Community Edition publishes a build on a JDK newer than 25 and Spring Boot declares support for it; Renovate proposes that as a major update.

**Sources:** EDI rewrite specification, sections 3.3 (assumption 2) and 5.5 (decision E19); `mise.toml`, `pom.xml`; evidence in [docs/verification/assumptions.md](../verification/assumptions.md).
