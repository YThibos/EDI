# ADR-028: Security and quality scanners in CI

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain: "since we have renovate, I would like to have OWASP DockSec too, a scanner for vulnerabilities. And while we are at it, a Sonar docker code scanner to keep the code clean."
Only open-source, standard tools are allowed (ADR-029), and an ephemeral SonarQube server keeps no history to compare new code against.

**Decision:** CI runs four scanners on every push and pull request:
- OWASP Dependency-Check through `dependency-check-maven`, failing the build on any finding at CVSS 7 or above and keeping its report as an artefact; its vulnerability database is restored before the scan and saved after it even when the scan fails, so a run that fails on a finding or a dropped download never forces the next one to start again from nothing; a free NVD API key is read from the Actions secret `NVD_API_KEY`, and requests to the NVD API are spaced 6 seconds apart with up to 20 retries, because the NVD answers a burst of requests with 403; the OSS Index analyser is off because it needs separate credentials, and the Node analysers are off because `npm audit` covers the console;
- `npm audit --audit-level=high` over the console's build dependencies;
- SonarQube Community Build as a service container (`sonarqube:<version>-community`), analysed through `sonar-maven-plugin` with JaCoCo coverage; `ops/sonar-setup.sh` replaces the default admin password, creates a token and makes the default quality gate one on overall code: reliability, security and maintainability rated A, and at most 3 % duplicated lines; the job fails when the gate fails;
- gitleaks and the personal-material checks of the publish guard (ADR-032).

The same SonarQube image runs locally for a deeper look, never required: `docker compose -f ops/sonar.yml up -d`, then `ops/sonar-setup.sh`, then `./mvnw -Psonar verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar` with the printed token in `SONAR_TOKEN`.
OWASP DockSec is not adopted: it analyses Dockerfiles and container images, EDI ships none, and its AI summary would call an external model service.

**Motivation:** Vulnerabilities and code-quality regressions show up as a red CI like any other failure.

**Alternatives considered:**
- SonarCloud - a hosted third-party service.
- A new-code quality gate - meaningless on a server with no history.
- DockSec now - nothing to scan.

**Consequences:**
- Good: the captain sees security and quality as part of green.
- Bad: the first Dependency-Check run, and the first run of every branch whose cache cannot see an earlier one, downloads the full vulnerability database of more than 400,000 records, which takes well over an hour even with an API key; the job's timeout of 150 minutes allows for it, and later runs only fetch the changes.
- Bad: coverage is reported but not gated yet; the console's TypeScript has no coverage report.

**Revisit when:** EDI or a project ships a container image (DockSec), or coverage becomes a gate condition.

**Sources:** EDI rewrite specification, section 5.7 (decision E23); `.github/workflows/ci.yml` (jobs `dependency-check`, `npm-audit`, `sonarqube`, `publish-guard`), `ops/sonar-setup.sh`, `ops/sonar.yml`, `pom.xml` (profile `sonar`, `dependency-check-maven`).
