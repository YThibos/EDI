# Java 25 on GraalVM Community, native executables, and three Maven modules

**Kind:** Architecture

## Context

Claude Code's hooks call the command on every turn, so its start-up time is paid constantly, while a native compile takes minutes.
On 15/10/2026 JDK 27 was the newest release, but GraalVM Community Edition published builds on the JDK 25 line only, and Spring Boot 4.1 supported Java up to 26.
Class files compiled for a newer JDK, preview features above all, cannot be compiled by an older JDK's `native-image`.
Module sprawl is one of the ways complexity creeps in, the command must stay free of Spring, and the console is an Angular application the core serves.

## Decision

EDI builds and runs on Java 25 with GraalVM Community Edition, pinned in `mise.toml` and set as `java.version` in `pom.xml`.
The `edi` command is always a native executable, and the core is installed as one; development and tests run on the JVM.
The Maven profile `native` compiles both executables and runs the native tests ([native-smoke-test](native-smoke-test.md)); if a native problem blocks a release, the core may run on the JVM until it is fixed, and the command never falls back.
`--enable-preview` is passed to the compiler, the tests, the Spring Boot plugin and `native-image`, and a preview feature EDI starts to use is recorded here with its JEP, in the change that first uses it; none is in use yet.

There are exactly three Maven modules under one parent POM that inherits `spring-boot-starter-parent` and pins every other plugin:

- `edi-console`, the Angular application, built by the frontend Maven plugin into a jar of static resources;
- `edi-core`, the Spring Boot application with the packages `domain`, `application`, `adapter.*` and `web`;
- `edi-cli`, the picocli command, without Spring.

The Maven wrapper pins Maven, the compiler runs with `-Xlint:all -Werror`, and the enforcer requires Java 25 or later, release versions for the project and every dependency, and no duplicate dependency declarations, so the version is `0.1.0` and never a SNAPSHOT.
The Maven group is `io.github.ythibos` and the Java namespace `io.github.ythibos.edi`.

## Why

The only open-source native toolchain is on JDK 25, so one JDK for the JVM build, the tests and the native images is the only combination that works ([open-source-tools](open-source-tools.md)).
A native command starts in milliseconds, so hooks cost nothing noticeable, while JVM tests keep `./mvnw verify` within minutes.
Preview on everywhere lets one change adopt a feature without touching the build, and the recorded list of features is the JDK upgrade checklist.
Three modules keep the boundaries that matter without one module per layer, and warnings as errors and enforced releases turn quality rules into build failures.

## Alternatives rejected

- Java 27 on the JVM without native images - every hook would pay a JVM start-up.
- Oracle GraalVM - not open source, and on JDK 25 too.
- Native everywhere, tests included - minutes per test run.
- One module per layer, or a shared library module - ceremony, and the command talks to the core over HTTP, so nothing needs sharing.

## Consequences

- Good: the native tests catch native-only failures, such as a `@Value` on a record component or an application class Spring proxied.
- Bad: ahead-of-time processing fixes the bean graph at build time, which shapes how machine differences are chosen ([machine-and-person-specifics](machine-and-person-specifics.md)) and forbids runtime class loading.
- Bad: EDI stays two releases behind the newest JDK, and class files that use a preview feature run only on the JDK that compiled them.
- Bad: every release needs an explicit version change.

## When to revisit

When GraalVM Community Edition publishes a build on a newer JDK that Spring Boot supports, when a preview feature EDI uses changes or becomes final, when JVM start-up becomes fast enough for hooks, or when a fourth module seems necessary.

## How we got here

- 15/10/2026: the captain chose the newest JDK and native images for fast start-up; the toolchain check found no native toolchain beyond JDK 25, so the specification's fallback to Java 25 applied, and slice S0 set up the modules, the build baseline and the native profile.
