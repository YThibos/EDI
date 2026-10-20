# ADR-146: Tests run git without the developer's own git configuration

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Git tests run in temporary repositories isolated from the developer's git configuration (AGENTS.md): `FixtureRepository` and the native tests' own git calls set `GIT_CONFIG_GLOBAL=/dev/null` and `GIT_CONFIG_NOSYSTEM=1`.
The git that EDI's own adapters start in the JVM tests, through `ExternalPrograms`, and the git that gitleaks and the stand-ins start, still read the developer's global and system configuration.
That configuration can change what a test proves, for example through `url.<base>.insteadOf` or `core.hooksPath`.
A global configuration that includes a file on a slow mounted file system made each of those git calls about seven times slower, and AT-38 alone makes over a hundred of them.

**Decision:** The shared surefire configuration in the root `pom.xml` sets `GIT_CONFIG_GLOBAL=/dev/null` and `GIT_CONFIG_NOSYSTEM=1` for every test JVM, so every git a JVM test starts, directly or through EDI's adapters, gitleaks or a stand-in, reads only the repository's own configuration.
`FixtureRepository` keeps its own isolation, and the native tests, which run under failsafe, keep theirs as they are.

**Motivation:** The tests prove the same thing on every machine, and a git call in a test costs what git costs, not what the developer's configuration costs.

**Alternatives considered:**
- Isolating git in `ExternalPrograms` - EDI must read the captain's configuration, for proxies and credentials.
- Setting the variables for each program a test starts - every helper and adapter would need them, and a forgotten one would read the developer's configuration again.
- Leaving it to each developer - the suite would still depend on whatever the machine's configuration holds.

**Consequences:**
- Good: no JVM test reads the developer's git configuration, and git-heavy tests run faster where that configuration is slow to read.
- Bad: a test that needs a git setting gives it through the repository's own configuration or the environment of the git it starts.

**Revisit when:** A test needs to prove behaviour that depends on the developer's global git configuration.

**Sources:** AGENTS.md, "Tests"; ADR-145; root `pom.xml` `maven-surefire-plugin` `environmentVariables`; `edi-core` `testing.FixtureRepository`, `adapter.process.ExternalPrograms`, `adapter.git.Git`; `edi-cli` `NativeSmokeIT`, `NativeRecoveryIT`.
