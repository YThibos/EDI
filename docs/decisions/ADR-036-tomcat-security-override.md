# ADR-036: Spring Boot's managed Tomcat is overridden for a security fix

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Spring Boot 4.1.1 manages `tomcat-embed-core` 11.0.24.
OWASP Dependency-Check fails the build on that version with CVE-2026-65183, CVE-2026-65905, CVE-2026-65927, CVE-2026-66422, CVE-2026-68763, CVE-2026-68525, CVE-2026-68569, CVE-2026-65182 and CVE-2026-65637, scored from 7.5 to 9.8.
The NVD lists them as affecting Tomcat 11.0.0 up to but excluding 11.0.25.
No Spring Boot release managing a fixed Tomcat is available yet.

**Decision:** The parent `pom.xml` sets the property `tomcat.version` to 11.0.27, overriding the version Spring Boot manages for every Tomcat artefact.
The findings are not suppressed and `failBuildOnCVSS` stays at 7 (ADR-028).
A regex custom manager in `renovate.json` keeps the property current from the Maven datasource for `org.apache.tomcat.embed:tomcat-embed-core`, because Renovate's Maven manager cannot see a property that only Spring Boot's BOM reads (ADR-027).

**Motivation:** The core serves its JSON API and the console over HTTP through this Tomcat, so the vulnerabilities are in the path EDI actually uses.
The scanner gate exists to fail on exactly this, and the answer it asks for is the fixed version, not a quieter gate.
Tomcat 11.0.27 is the fixed line available on Maven Central.

**Alternatives considered:**
- Waiting for a Spring Boot patch - leaves a known critical vulnerability in the core and the CI gate red until it ships.
- Suppressing the findings - hides real vulnerabilities in code EDI runs and defeats the gate.

**Consequences:**
- Good: the core runs a Tomcat without the known vulnerabilities and the Dependency-Check gate stays meaningful.
- Bad: Tomcat no longer moves with Spring Boot, so the pair is no longer the combination Spring Boot tested, and the override must be removed by hand once it is no longer needed.

**Revisit when:** Spring Boot manages Tomcat 11.0.25 or later; the `tomcat.version` property and its Renovate manager are then removed.

**Sources:** OWASP Dependency-Check report of the `dependency-check` CI job; `pom.xml` (property `tomcat.version`), `renovate.json` (regex manager for `tomcat.version`).
