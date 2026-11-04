# Working on EDI

Rules for agents changing this repository.
The why behind every rule is in [docs/decisions](docs/decisions/README.md); read the records that govern the code you touch before changing it.

## Build and test

- `mise install` installs the pinned toolchain: GraalVM Community Edition for Java 25, Node for the console build, gitleaks.
- `./mvnw verify` runs the JVM build and every test; run it before every commit.
- `./mvnw test -Pfast` runs the domain, application and architecture tests only.
- `./mvnw -Pnative,quick-build verify` builds both native executables in GraalVM's quick-build mode and runs the native smoke test; run it only when the change can reach the native image, as `NativeImageInput` lists, and otherwise say in the pull request why it was not needed ([local-native-builds](docs/decisions/local-native-builds.md)).
- `./mvnw -Pnative verify` builds the full optimised executables, as CI and the install do.

## Queued work

- The queued and held EDI work is one GitHub issue per item on this repository, each carrying its full spec; `node ops/backlog/pickup.mjs --next` names the issues a session may take, in order, and [docs/pickup.md](docs/pickup.md) is the pickup protocol and the trust rule; a session that has only this repository starts there ([backlog-in-the-repository](docs/decisions/backlog-in-the-repository.md)).

## Commits and branches

- Branches are `feature|chore|research/EDI-<n>`, never `fm/` or `edi/`.
- Every commit subject starts with the work key, for example `EDI-2 Add the port inventory`; dependency upkeep uses `EDI-1`.
- No trailer of any kind: no `Co-authored-by`, no `Signed-off-by` naming a model or tool, no "Generated with" line.
- No SNAPSHOT version anywhere; the enforcer plugin refuses one.
- Never push to `main`, never force-push, never merge into `main`; deliveries are draft pull requests the captain merges.

## Code

- No explanatory comments; only licence headers, suppression justifications, and TODO or FIXME lines that carry an EDI key ([decision-log](docs/decisions/decision-log.md)).
- Names carry the domain's language; methods are short and do one thing; public methods read as a sequence of named steps, with private helpers below their callers in order of first use.
- Exactly six ports live in `application.port`; a seventh needs its decision recorded first ([ports-and-architecture-rules](docs/decisions/ports-and-architecture-rules.md)).
- The architecture rules in `ArchitectureRulesTest` are not to be bent: the domain stays free of Spring, input, output and processes; only `adapter.process` starts programs, each program only from its owning adapter; every time read goes through the `Clock` port ([ports-and-architecture-rules](docs/decisions/ports-and-architecture-rules.md)).
- Machine differences come from the machine profile at runtime, never from `@Profile` or `@ConditionalOnProperty` ([machine-and-person-specifics](docs/decisions/machine-and-person-specifics.md)).
- A preview feature, used for the first time, is recorded with its JEP ([java-toolchain-and-build](docs/decisions/java-toolchain-and-build.md)).

## Tests

- Tests are fast and deterministic: time comes from the `Clock` port, no `Thread.sleep` except in a helper that waits for a real child process with a hard timeout, a wait for real work waits on its event up to `Patience.FOR_REAL_WORK` and never asserts how soon it came, and a test does a few seconds of work at most on an idle machine; the 3-minute default timeout only catches a hang.
- A test that needs an external tool skips with a reason when the tool is missing; it never fails for that.
- Git tests use temporary repositories isolated from the user's git configuration (`FixtureRepository`).
- The invariant catalogue lives in `edi-core`'s test package `invariant`; a slice makes an acceptance test pass by replacing its `Pending.until` call with the real test ([invariant-catalogue](docs/decisions/invariant-catalogue.md)).

## Writing

- United Kingdom English outside syntax and vendor terms, a plain `-` and never an em dash, and one sentence per line in Markdown.
- The decision log holds architecture and design decisions only, one record per decision in a file named by its topic, changed in place with a line in its history ([docs/decisions/README.md](docs/decisions/README.md)).
- Tracked files never name a particular person, employer, account, host or machine path; those belong in the EDI home or the gitignored `personal/` directory ([machine-and-person-specifics](docs/decisions/machine-and-person-specifics.md)).
- This file stays small: it is loaded into every agent session, and the anchor budget test keeps it at 10,000 bytes or less.
