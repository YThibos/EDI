# Java 25 on GraalVM Community, native executables, and three Maven modules built by Maven 4

**Kind:** Architecture

## Context

Claude Code's hooks call the command on every turn, so its start-up time is paid constantly, while a native compile takes minutes.
On 15/10/2026 JDK 27 was the newest release, but GraalVM Community Edition published builds on the JDK 25 line only, and Spring Boot 4.1 supported Java up to 26.
On 27/10/2026 that still held: GraalVM Community's releases ([graalvm-ce-builds](https://github.com/graalvm/graalvm-ce-builds/releases)) and its [release calendar](https://www.graalvm.org/release-calendar/) list only monthly feature releases on the 25 line, the newest GraalVM Community 25 Innovation 4 (graal 25.4.4.1.1, JDK 25.0.4.1.1), planned through February 2027, and no build on JDK 26 or 27.
mise's `java` plugin indexes GraalVM Community only up to 25.0.2, and does not list the feature releases.
Class files compiled for a newer JDK, preview features above all, cannot be compiled by an older JDK's `native-image`.
Module sprawl is one of the ways complexity creeps in, the command must stay free of Spring, and the console is an Angular application the core serves.

## Decision

EDI builds and runs on Java 25 with GraalVM Community Edition, set as `java.version` in `pom.xml` and pinned in `mise.toml` to its newest release through mise's `http` backend, `http:graalvm-community`, with each platform's archive and checksum and `JAVA_HOME` set from the installed tool.
The `edi` command is always a native executable, and the core is installed as one; development and tests run on the JVM.
The Maven profile `native` compiles both executables and runs the native tests ([native-smoke-test](native-smoke-test.md)), with the full optimisation unless the `quick-build` profile asks for GraalVM's quick-build mode ([local-native-builds](local-native-builds.md)); if a native problem blocks a release, the core may run on the JVM until it is fixed, and the command never falls back.
`--enable-preview` is passed to the compiler, the tests, the Spring Boot plugin and `native-image`, and a preview feature EDI starts to use is recorded here with its JEP, in the change that first uses it; none is in use yet.

There are exactly three Maven modules under one parent POM that inherits `spring-boot-starter-parent` and pins every other plugin:

- `edi-console`, the Angular application, built by the frontend Maven plugin into a jar of static resources;
- `edi-core`, the Spring Boot application with the packages `domain`, `application`, `adapter.*` and `web`;
- `edi-cli`, the picocli command, without Spring.

The Maven wrapper pins Maven 4, now 4.0.0-rc-7, the compiler runs with `-Xlint:all -Werror`, and the enforcer requires Java 25 or later, Maven 4.0.0-rc-7 or later, release versions for the project and every dependency, and no duplicate dependency declarations, so the version is `0.1.0` and never a SNAPSHOT.
Every POM uses model 4.1.0: the parent lists its `subprojects`, a module's `<parent/>` is inferred from the directory above, a dependency on a sibling module carries no version, and paths from the repository root use `${session.rootDirectory}`; installing or deploying would publish Maven 4's generated consumer POM, on model 4.0.0, so nothing reading the artefacts needs Maven 4.
`.mvn/maven.config` passes `--ignore-transitive-repositories`, so dependencies resolve from Maven Central and the repositories the build names, never from one a dependency's POM declares.
The Maven group is `io.github.ythibos` and the Java namespace `io.github.ythibos.edi`.

## Why

The only open-source native toolchain is on JDK 25, so one JDK for the JVM build, the tests and the native images is the only combination that works ([open-source-tools](open-source-tools.md)).
The feature releases are stable releases with the newest compiler and Native Image, and the `http` backend installs one with a verified checksum where the `java` plugin cannot.
Maven 4 removes the repetition model 4.0.0 needs, a parent's coordinates and a sibling's version in every module, and its consumer POM keeps that from leaking to anything that reads the artefacts.
A repository a dependency declares is one EDI never chose, and probing it slowed every resolution and warned when it asked for credentials.
A native command starts in milliseconds, so hooks cost nothing noticeable, while JVM tests keep `./mvnw verify` within minutes.
Preview on everywhere lets one change adopt a feature without touching the build, and the recorded list of features is the JDK upgrade checklist.
Three modules keep the boundaries that matter without one module per layer, and warnings as errors and enforced releases turn quality rules into build failures.

## Alternatives rejected

- Java 27 on the JVM without native images - every hook would pay a JVM start-up.
- Oracle GraalVM - not open source, and on JDK 25 too.
- GraalVM Community 25.0.2 through mise's `java` plugin - the plugin's newest GraalVM, four feature releases behind.
- Staying on Maven 3 until Maven 4.0.0 is final - the captain chose to start on Maven 4 now, and the move is cheaper while the build is small.
- Native everywhere, tests included - minutes per test run.
- One module per layer, or a shared library module - ceremony, and the command talks to the core over HTTP, so nothing needs sharing.

## Consequences

- Good: the native tests catch native-only failures, such as a `@Value` on a record component or an application class Spring proxied.
- Bad: ahead-of-time processing fixes the bean graph at build time, which shapes how machine differences are chosen ([machine-and-person-specifics](machine-and-person-specifics.md)) and forbids runtime class loading.
- Bad: EDI stays two releases behind the newest JDK, and class files that use a preview feature run only on the JDK that compiled them.
- Bad: every release needs an explicit version change.
- Bad: a GraalVM upgrade changes each platform's archive name and checksum by hand, since a feature release's archive name carries its JDK version; Renovate proposes the version, and the install fails until the archives match ([dependency-upkeep-and-scanners](dependency-upkeep-and-scanners.md)).
- Bad: mise installs from a URL and sets `JAVA_HOME` only for a `mise.toml` it trusts, so a clone needs `mise trust` once, and a worker's copy inherits the clone's trust as its git worktree ([worker-sessions](worker-sessions.md)).
- Bad: Maven 4 is still a release candidate, and the build is Maven 4 only, so a Maven 3 on the `PATH` cannot build EDI; `./mvnw` always can.

## When to revisit

When GraalVM Community Edition publishes a build on a newer JDK that Spring Boot supports, when mise's `java` plugin indexes GraalVM's feature releases, when Maven 4.0.0 is final or the build would gain from Maven 4's concurrent builder, its `before:` and `after:` phases or the 4.0.0 core plugins, still unused, when a preview feature EDI uses changes or becomes final, when JVM start-up becomes fast enough for hooks, or when a fourth module seems necessary.

## How we got here

- 15/10/2026: the captain chose the newest JDK and native images for fast start-up; the toolchain check found no native toolchain beyond JDK 25, so the specification's fallback to Java 25 applied, and slice S0 set up the modules, the build baseline and the native profile.
- 27/10/2026: a worker's local native check moved to quick-build mode through the `quick-build` profile, while CI and the install keep the optimised build (EDI-99).
- 27/10/2026: no GraalVM Community build exists on JDK 27 upstream, so EDI moved to GraalVM Community 25.4.4.1.1 through mise's `http` backend, Maven 4.0.0-rc-7 and model 4.1.0 (EDI-100).
