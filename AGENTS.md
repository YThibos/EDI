# Working on EDI

Rules for agents changing this repository.
The why behind every rule is in [docs/decisions](docs/decisions/README.md); read the records that govern the code you touch before changing it.

## Build and test

- `mise install` installs the pinned toolchain: GraalVM Community Edition for Java 25, Node for the console build, gitleaks.
- `./mvnw verify` runs the JVM build and every test; run it before every commit.
- `./mvnw test -Pfast` runs the domain, application and architecture tests only.
- `./mvnw -Pnative verify` builds both native executables and runs the native smoke test; run it when a change touches Spring configuration, reflection, resources or the command.

## Commits and branches

- Branches are `feature|chore|research/EDI-<n>`, never `fm/` or `edi/`.
- Every commit subject starts with the work key, for example `EDI-2 Add the port inventory`; dependency upkeep uses `EDI-1`.
- No trailer of any kind: no `Co-authored-by`, no `Signed-off-by` naming a model or tool, no "Generated with" line.
- No SNAPSHOT version anywhere; the enforcer plugin refuses one.
- Never push to `main`, never force-push, never merge; deliveries are draft pull requests the captain merges.

## Code

- No explanatory comments; only licence headers, suppression justifications, and TODO or FIXME lines that carry an EDI key (ADR-033).
- Names carry the domain's language; methods are short and do one thing; public methods read as a sequence of named steps, with private helpers below their callers in order of first use.
- Exactly six ports live in `application.port`; a seventh needs a new decision record (ADR-007).
- The architecture rules in `ArchitectureRulesTest` are not to be bent: the domain stays free of Spring, input, output and processes; only `adapter.process` starts programs, each program only from its owning adapter; every time read goes through the `Clock` port (ADR-009).
- Machine differences come from the machine profile at runtime, never from `@Profile` or `@ConditionalOnProperty` (ADR-006).
- A preview feature, used for the first time, gets its own decision record naming its JEP (ADR-004).

## Tests

- Tests are fast and deterministic: time comes from the `Clock` port, no `Thread.sleep` except in a helper that waits for a real child process with a hard timeout, and every test class finishes within the 30-second default timeout.
- A test that needs an external tool skips with a reason when the tool is missing; it never fails for that.
- Git tests use temporary repositories isolated from the user's git configuration (`FixtureRepository`).
- The invariant catalogue lives in `edi-core`'s test package `invariant`; a slice makes an acceptance test pass by replacing its `Pending.until` call with the real test (ADR-034).

## Writing

- United Kingdom English outside syntax and vendor terms, a plain `-` and never an em dash, and one sentence per line in Markdown.
- Decision records are append-only; a changed decision is a new record that supersedes the old one (docs/decisions/README.md).
- Tracked files never name a particular person, employer, account, host or machine path; those belong in the EDI home or the gitignored `personal/` directory (ADR-017).
- This file stays small: it is loaded into every agent session, and the anchor budget test keeps it at 10,000 bytes or less.
