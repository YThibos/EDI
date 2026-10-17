# ADR-102: The full suite runs under a laptop and a desktop fixture profile

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-37 has one codebase serve both machines, with every machine difference in the machine profile and the registry, and AT-37 runs the full test suite green under both the `laptop` and the `desktop` fixture profiles.

**Decision:** The test resources hold two fixture profiles: `laptop`, with a JIRA tracker, evergreen tickets, a workflow, the fleet ledger and the worked line, and `desktop`, with no tracker, no ledger and no worked line; both allow the same models.
Every test home is written with the profile the system property `edi.test.machine` names, and the core module's Surefire runs the whole suite twice in `./mvnw verify`, once per profile, the desktop run reporting to `target/surefire-reports-desktop`; a test about one machine's behaviour writes that machine's profile itself.
AT-37 checks that the build runs both, that this run is under one of them, and that the profiles differ only in the tracker, the ledger and the worked line.

**Motivation:** A code path that assumed the laptop, or the desktop, fails the run of the other profile, which a single run under either could never show.

**Alternatives considered:**
- Parameterising every test by profile - every existing test would change.
- One run under each profile in CI only - a local `./mvnw verify` would not catch the failure.

**Consequences:**
- Good: the suite proves the same use cases on both machines in every build.
- Bad: the core's tests run twice, which adds about forty seconds to `./mvnw verify`.

**Revisit when:** A third machine profile appears, or the suite grows slow enough for the second run to matter.

**Sources:** EDI rewrite specification, sections 3.2, 7.2 and I-37 (AT-37); ADR-006; `edi-core/pom.xml`, `edi-core/src/test/resources/machine-profiles/`, `testing.MachineFixture`, `testing.TestHome`, `invariant.TwoMachinesTest`, `.github/workflows/ci.yml`.
