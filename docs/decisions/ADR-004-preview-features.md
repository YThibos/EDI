# ADR-004: Preview features are on, and each adopted one gets its own record

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain: "Especially if they allow for more efficient or more readable and maintainable code, we should use preview features for sure!"
A preview API can change or disappear in the next JDK, so whoever upgrades the JDK must know exactly which preview features EDI relies on.
EDI builds on JDK 25 (ADR-003) and targets JDK 27 when a native toolchain exists for it.

**Decision:** The build passes `--enable-preview` to the compiler, the tests, the Spring Boot plugin and `native-image`.
Every preview feature EDI starts to use gets its own record naming its JEP, in the change that first uses it.
No preview feature is in use yet.

The preview and incubator JEPs of JDK 25, the JDK in use:

| JEP | Feature | Candidate use in EDI |
|---|---|---|
| 470 | PEM Encodings of Cryptographic Objects (Preview) | none foreseen |
| 502 | Stable Values (Preview) | lazily computed configuration such as the machine profile |
| 505 | Structured Concurrency (Fifth Preview) | supervision rounds that query several sources at once |
| 507 | Primitive Types in Patterns, instanceof, and switch (Third Preview) | none foreseen |
| 508 | Vector API (Tenth Incubator) | none foreseen |

The preview and incubator JEPs of JDK 27, the JDK EDI moves to next (general availability 15/09/2026):

| JEP | Feature |
|---|---|
| 531 | Lazy Constants (Third Preview), the successor of Stable Values |
| 532 | Primitive Types in Patterns, instanceof, and switch (Fifth Preview) |
| 533 | Structured Concurrency (Seventh Preview) |
| 537 | Vector API (Twelfth Incubator) |
| 538 | PEM Encodings of Cryptographic Objects (Third Preview) |

**Motivation:** Turning preview on everywhere from the start means a feature can be adopted by one change without touching the build, and the per-feature record keeps the upgrade risk visible.
Stable Values became Lazy Constants between 25 and 27, which is exactly the kind of change a record per feature lets the next upgrade find.

**Alternatives considered:**
- Preview off until a feature is needed - every adoption would also be a build change, and the native and test configuration would drift.
- Adopt candidate features now - nothing in slice S0 needs them; adopting them without a caller would be speculative code.

**Consequences:**
- Good: the JDK upgrade checklist is the list of preview records.
- Bad: class files that use a preview feature run only on the exact JDK they were compiled with, so the JVM, the tests and `native-image` must stay on one JDK (ADR-003).

**Revisit when:** A preview feature EDI uses becomes final, changes or is withdrawn, or the JDK moves.

**Sources:** EDI rewrite specification, section 5.5 (decision E19); JDK 25 and JDK 27 project pages on openjdk.org; `pom.xml` (`argLine`, `maven-compiler-plugin`, `spring-boot-maven-plugin`, `native-maven-plugin`).
