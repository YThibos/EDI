# Development

[AGENTS.md](../AGENTS.md) holds the rules for agents changing EDI, and [docs/decisions](decisions/README.md) holds every architecture and design decision with its why.

## Toolchain

The toolchain is pinned in [mise.toml](../mise.toml): GraalVM Community Edition for Java 25, the Node that builds the console, and gitleaks ([ADR-003](decisions/ADR-003-java-baseline.md), [ADR-142](decisions/ADR-142-the-install-builds-with-the-toolchain-the-checkout-pins.md)).
Maven runs through its wrapper, and Node runs only in the console's build, never at run time ([ADR-014](decisions/ADR-014-no-node-in-the-runtime.md)).

## Build and test

```sh
mise install
./mvnw verify               # the JVM build and every test
./mvnw test -Pfast          # domain, application and architecture tests only
./mvnw -Pnative verify      # both native executables, then the native smoke test
```

`./mvnw verify` runs the whole invariant catalogue; an acceptance test a later slice owns would be reported as skipped ([ADR-034](decisions/ADR-034-invariant-catalogue.md)).
The suite runs under both machine profiles ([ADR-102](decisions/ADR-102-the-suite-runs-under-both-machine-profiles.md)), Claude Code is replaced by a stand-in in the tests ([ADR-064](decisions/ADR-064-the-stand-in-for-claude-code.md)), and tests stop every process they start ([ADR-065](decisions/ADR-065-tests-stop-every-process-they-start.md)).
The native smoke test runs the built executables end to end ([ADR-045](decisions/ADR-045-native-round-trip-in-the-smoke-test.md)).

## Continuous integration

`.github/workflows/ci.yml` builds and tests on the JVM, builds the native executables and runs the smoke test, and runs four scanners on every push and pull request ([ADR-028](decisions/ADR-028-security-and-quality-scanners.md)): OWASP Dependency-Check, `npm audit` over the console's build dependencies, a SonarQube Community Build quality gate, and the publish guard with gitleaks ([ADR-032](decisions/ADR-032-publish-guard.md)).
The same SonarQube image runs locally for a deeper look: `docker compose -f ops/sonar.yml up -d`, then `ops/sonar-setup.sh`, then `./mvnw -Psonar verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar` with the printed token in `SONAR_TOKEN`.
`ops/catalogue-summary.sh` reports the acceptance tests still pending, and `ops/main-code-size.sh` reports the size of the main code.

## Dependency upkeep

Renovate runs from `.github/workflows/renovate.yml` every night and opens draft pull requests under the work key `EDI-1`, never merging one itself ([ADR-027](decisions/ADR-027-renovate-upkeep.md)); its rules are in [renovate.json](../renovate.json).

## Code and writing

The code carries no explanatory comments ([ADR-033](decisions/ADR-033-no-explanatory-comments.md)): a decision record names the classes, files and settings it governs, so a search for any of those names finds the why.
Nothing tracked names a particular person, employer, account, host or machine path; those belong in the EDI home or the gitignored `personal/` directory ([ADR-017](decisions/ADR-017-person-and-employer-agnostic.md)).
