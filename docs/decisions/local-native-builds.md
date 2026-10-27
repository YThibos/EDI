# A worker builds the native image only when its change can reach it, in quick-build mode, and EDI warns of heavy runs at once instead of capping them

**Kind:** Design

## Context

A native build of both executables takes minutes and most of the machine's memory and cores, and the JVM verify already runs every test.
Most changes cannot alter what the native image holds: only reflection or serialisation-bound types, resources the image reads, Spring configuration, native-image configuration, dependencies and build files can.
GraalVM's quick-build mode (`-Ob`) builds an image much faster with fewer optimisations, which proves the image builds and starts but is not the image EDI installs.
Several workers once ran native builds and test runs at the same time and slowed each other down; such a pile-up is rare, and the captain chose a warning over a cap that would slow every native build and test run.
Every native build builds both executables, whichever module changed.

## Decision

`NativeImageInput` lists what can reach the native image, and it is the one place that rule lives.
A project registers its local native build as `nativeBuild` in the project registry (`ProjectEntry.nativeBuild`); a ship's brief for such a project then says, from `NativeImageInput`, to run that build only when the change touches one of those inputs, and otherwise to take the project's `verify` as the local evidence and say why the native build was not needed: in the merge request body, leaving the full optimised native build to CI, or, for a delivery without a merge request, in the task's report (`BriefSections.delivery`).
The `quick-build` Maven profile sets `native.optimisation` to `-Ob`, which the root `pom.xml` passes to `native-maven-plugin`; without it the build keeps `-O2`, so CI and `ops/install.sh` build the full optimised image with `./mvnw -Pnative verify`, and a worker's local check is `./mvnw -Pnative,quick-build verify`.
`NoticeHeavyRunsAtOnce`, in every supervision round, reads the argument lists of each serving worker's pane and everything under it (`ProcessTable.argumentsInTree`), skipping a worker whose recorded pane id no longer belongs to its tmux server (`ProcessTable.isStillThePaneOf`); `HeavyRun.of` reads a Maven launch whose profiles name `native` as a native build and any other Maven launch through a testing phase as a test run, and keeps the heavier run of each worker (`HeavyRuns`).
When two or more workers run one at once, every worker whose run started since the last round records `HeavyRunsAtOnce`, which wakes the primary with what runs beside it, and the captain report heads itself with a `Builds:` line while they overlap (`CaptainReport.heavyRunsAtOnce`).
Nothing limits the memory, cores or number of native builds or test runs.

## Why

A change that cannot reach the image gains nothing from a local native build, and one list read by the brief cannot drift from the rule a reviewer checks.
Quick-build mode keeps the local check's question, whether the image builds and its smoke test passes, at a fraction of the time, while what is installed is still the optimised image.
A warning costs nothing when builds do not overlap, which is nearly always, and still tells the captain why the fleet is slow when they do.

## Alternatives rejected

- A path-based classifier that decides from the changed files - reflection-bound types live in ordinary source files, so the paths alone cannot tell.
- Building only the executable whose module changed - both executables share the core's code and are tested together by the smoke test.
- A cap on concurrent native builds, or memory and parallelism limits on them and on the tests - slows every build to guard against a rare pile-up.
- Quick-build mode everywhere - CI and the installed image would no longer be the optimised build.
- Detecting heavy runs from the commands the guard sees - a background build's end is never seen, so an overlap could not be told from a finished run.

## Consequences

- Good: most workers skip minutes of native build, and a native check that must run is several times faster.
- Good: the captain and the primary learn of a pile-up within one round, without any build being slowed by EDI.
- Bad: a change that reaches the image through a path the list does not name, judged by the worker, is found only by CI or the next install.
- Bad: only Maven launches in a worker's own pane are seen, so a run inside the validation pipeline or another build tool goes unnoticed.

## When to revisit

When a native-only failure reaches `main` that the list would have missed, when CI stops running the native build, or when a project with another build tool registers a native build.

## How we got here

- 27/10/2026: native builds and test runs from several workers overlapped and slowed each other; the captain chose builds only when a change can reach the image, quick-build mode locally, every executable built, and a warning instead of a cap (EDI-99).
