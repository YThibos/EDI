# ADR-008: Three Maven modules and the build baseline

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** Module sprawl is one of the ways complexity creeps into a codebase over time.
The command must stay free of Spring so it compiles to a small, fast native executable, and the console is an Angular application that the core serves.
The captain's rules forbid SNAPSHOT versions in push-bound work (I-11) and expect lint warnings to be fixed, not tolerated.

**Decision:** Exactly three Maven modules under one parent POM:
- `edi-console`, the Angular application, built by the frontend Maven plugin and packaged as a jar of static resources under `static/`;
- `edi-core`, the Spring Boot application with the packages `domain`, `application`, `adapter.*` and `web`, which depends on the console jar at runtime;
- `edi-cli`, the picocli command, with no Spring.

The parent inherits from `spring-boot-starter-parent` and pins every other plugin version.
The Maven wrapper pins Maven 3.10.0, and the enforcer plugin requires Java 25 or later, Maven 3.9 or later, release versions for the project and every dependency, and no duplicate dependency declarations.
The compiler runs with `-Xlint:all -Werror`.
The Maven group is `io.github.ythibos` and the Java namespace is `io.github.ythibos.edi`, a namespace the captain demonstrably owns through the GitHub account that hosts the repository.
The project version is `0.1.0`, because SNAPSHOT coordinates are refused.

**Motivation:** Three modules keep the boundaries that matter (no Spring in the command, the console as a build artefact) without one module per layer.
Warnings as errors and enforced release versions turn two of the captain's quality rules into build failures instead of review comments.

**Alternatives considered:**
- One module per layer - ceremony the specification explicitly rejects.
- A shared library module for code the command and the core both need - the command talks to the core over HTTP, so nothing needs sharing.

**Consequences:**
- Good: a new warning or a SNAPSHOT dependency fails the build at once.
- Bad: every release needs an explicit version change, since SNAPSHOT versions are not available.

**Revisit when:** A fourth module seems necessary; that is a new record.

**Sources:** EDI rewrite specification, sections 3.3 (assumption 8), 5.3 and 6, and I-11; `pom.xml`, `edi-console/pom.xml`, `edi-core/pom.xml`, `edi-cli/pom.xml`, `.mvn/wrapper/maven-wrapper.properties`.
