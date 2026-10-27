# Development

[AGENTS.md](../AGENTS.md) holds the rules for agents changing EDI, and [docs/decisions](decisions/README.md) holds every architecture and design decision with its why.

## Toolchain

The toolchain is pinned in [mise.toml](../mise.toml): GraalVM Community Edition for Java 25, the Node that builds the console, and gitleaks ([java-toolchain-and-build](decisions/java-toolchain-and-build.md), [core-lifecycle](decisions/core-lifecycle.md)).
Maven 4 runs through its wrapper, since the POMs use model 4.1.0 that Maven 3 cannot read, and Node runs only in the console's build, never at run time ([open-source-tools](decisions/open-source-tools.md)).

## Build and test

```sh
mise trust                  # once per clone, see java-toolchain-and-build
mise install
./mvnw verify               # the JVM build and every test
./mvnw test -Pfast          # domain, application and architecture tests only
./mvnw -Pnative verify      # both native executables, then the native smoke test
./mvnw -Pnative,quick-build verify   # the same in GraalVM's quick-build mode, the local native check
```

`./mvnw verify` runs the whole invariant catalogue; an acceptance test a later slice owns would be reported as skipped ([invariant-catalogue](decisions/invariant-catalogue.md)).
The suite runs under both machine profiles ([machine-and-person-specifics](decisions/machine-and-person-specifics.md)), Claude Code is replaced by a stand-in in the tests ([native-smoke-test](decisions/native-smoke-test.md)), and tests stop every process they start ([test-isolation](decisions/test-isolation.md)).
The native smoke test runs the built executables end to end ([native-smoke-test](decisions/native-smoke-test.md)).
A local native check runs in quick-build mode, and only when the change can reach the native image; CI and the install build the optimised image ([local-native-builds](decisions/local-native-builds.md)).

## Continuous integration

`.github/workflows/ci.yml` builds and tests on the JVM, builds the native executables and runs the smoke test, and runs four scanners on every push and pull request ([dependency-upkeep-and-scanners](decisions/dependency-upkeep-and-scanners.md)): OWASP Dependency-Check, `npm audit` over the console's build dependencies, a SonarQube Community Build quality gate, and the publish guard with gitleaks ([publish-guard](decisions/publish-guard.md)).
The same SonarQube image runs locally for a deeper look: `docker compose -f ops/sonar.yml up -d`, then `ops/sonar-setup.sh`, then `./mvnw -Psonar verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar` with the printed token in `SONAR_TOKEN`.
`ops/catalogue-summary.sh` reports the acceptance tests still pending, and `ops/main-code-size.sh` reports the size of the main code.

## Dependency upkeep

Renovate runs from `.github/workflows/renovate.yml` every night and opens draft pull requests under the work key `EDI-1`, never merging one itself ([dependency-upkeep-and-scanners](decisions/dependency-upkeep-and-scanners.md)); its rules are in [renovate.json](../renovate.json).

## Code and writing

The code carries no explanatory comments ([decision-log](decisions/decision-log.md)): a decision record names the classes, files and settings it governs, so a search for any of those names finds the why.
Nothing tracked names a particular person, employer, account, host or machine path; those belong in the EDI home or the gitignored `personal/` directory ([machine-and-person-specifics](decisions/machine-and-person-specifics.md)).
