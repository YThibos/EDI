# ADR-029: Open-source, standard tools only

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain: "Open-source and standardised tools only."
He also declined third-party routing services: "as few third-party services as possible".

**Decision:** EDI is built with GraalVM Community Edition, Spring Boot and Spring Framework, picocli, SQLite with the Xerial driver, Flyway Community, Jackson, Angular, JUnit, AssertJ, ArchUnit, Maven with its wrapper, Renovate, OWASP Dependency-Check, npm audit, SonarQube Community Build, gitleaks and mise.
The external programs EDI drives stay as they are: git, tmux, the GitLab and GitHub command-line tools, the captain's validation pipeline and research-board tool, and Claude Code itself, the only proprietary piece.
No hosted third-party service is added.

**Motivation:** Every dependency can be inspected, pinned and kept running without a vendor relationship.

**Alternatives considered:**
- Oracle GraalVM - not open source (ADR-003).
- Hosted scanners and dependency bots - third-party services.

**Consequences:**
- Good: no licence or account can switch a part of EDI off.
- Bad: EDI self-hosts what a hosted service would run for it, such as Renovate and SonarQube.

**Revisit when:** A needed capability has no open-source tool.

**Sources:** EDI rewrite specification, section 5.6.
