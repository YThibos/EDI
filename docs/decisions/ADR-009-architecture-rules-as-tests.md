# ADR-009: Architecture rules are executable tests

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Every incident in the old harness's history came from a safety rule that drifted, and a rule written only in prose drifts.
ArchUnit can check package dependencies, method calls, field access and annotations of the compiled classes, but it cannot read string constants, so a rule such as "only `adapter.forge` runs `gh`" needs the program to be visible as a type or a field.

**Decision:** `ArchitectureRulesTest` checks the architecture on every build:
- the domain depends only on itself and the core Java library, and touches no process, system, thread or reflection API;
- only `adapter.process` starts programs;
- external programs are the constants of the closed enum `Program`, which is the only way to start one, and each constant may be used only by the adapter that owns it: `GIT` by `adapter.git`, `GITLEAKS` by `adapter.gitleaks`, `TMUX` and `CLAUDE_CODE` by `adapter.runtime`, `GLAB` and `GH` by `adapter.forge`; a constant without an owner fails the test;
- every time read goes through the `Clock` port (no `now()` from `java.time`, no `System.currentTimeMillis` or `System.nanoTime`, no `new Date()` outside `adapter.clock`);
- no class or method anywhere in EDI names a merge operation, which is the method-name half of AT-03a (`NameAMergeOperation`): a name is refused when it starts or ends with the verb `merge`, when `merge` follows a verb that performs one (such as `acceptMergeRequest` or `squashAndMerge`), or when it names auto-merge or merge-when-pipeline-succeeds, while names that only observe a merge, such as `isMerged`, `mergedAt` or `mergeRequestState`, pass; `edi-core` shares the condition through its test jar, so `CommandArchitectureRulesTest` applies the same rule to every class of `edi-cli`;
- nothing loads classes at runtime, and nothing chooses machine differences at build time (ADR-005, ADR-006);
- every interface in `application` is a port, only adapters implement ports, adapters reach the application only through ports, and nothing depends on the web layer.

The rules analyse hand-written classes only (`HandWrittenClasses`), since the `native` profile compiles Spring's ahead-of-time generated classes into the same output directory.

**Motivation:** A rule that fails the build cannot drift.
A closed enum of programs gives ArchUnit something it can see, so "who runs what" becomes a checked fact instead of a convention.

**Alternatives considered:**
- Searching source files for program names - text matching proves nothing about behaviour and breaks on harmless refactors.
- Review-only rules - the old harness shows where that leads.

**Consequences:**
- Good: the rules hold from slice S0 onwards, before the adapters they govern exist.
- Bad: adding an external program means adding a `Program` constant and its owner in the test, which is deliberate friction.

**Revisit when:** A rule blocks a legitimate design; change it with a new record rather than an exception.

**Sources:** EDI rewrite specification, sections 5.3, 5.5, 17.3 and AT-03a; `edi-core/src/test/java/io/github/ythibos/edi/architecture/`, `edi-cli/src/test/java/io/github/ythibos/edi/cli/CommandArchitectureRulesTest.java`, `edi-core/src/main/java/io/github/ythibos/edi/adapter/process/Program.java`.
