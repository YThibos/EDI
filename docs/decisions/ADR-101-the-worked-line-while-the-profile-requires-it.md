# ADR-101: The brief asks for the worked line only while the machine profile requires it

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Until the time-booking tool's continuous capture is confirmed on, a worker's report ends with a `worked:` line, `worked: <start> -> <end> = <H>h<MM>m`; the settled design retires it afterwards, so section 14.3 makes it depend on `time.requireWorkedLine` in the machine profile.

**Decision:** `LaunchWorker` reads `time.requireWorkedLine` at every launch, and while it is true the brief's definition of done asks the worker to end `report.md` with that line, the local times it started and finished written as `YYYY-MM-DDTHH:MM`, before it reports done; while it is false or absent, the brief says nothing about it.

**Motivation:** Turning the line off is one edit of the profile the day continuous capture is confirmed, with no change to the harness.

**Alternatives considered:**
- Asking for the line always - the captain retires it once measured time replaces it.
- Writing the line from EDI's own records - booking stays with the time-booking tool, which already measures the time from its own sources.

**Consequences:**
- Good: the desktop, which books no time, never asks for the line.
- Bad: the line is still a model's claim while it is required, which is why it is temporary.

**Revisit when:** The captain confirms continuous capture and sets `requireWorkedLine` to false.

**Sources:** EDI rewrite specification, sections 7.2, 8.7 and 14.3; ADR-055; `application.brief.BriefFacts`, `BriefSections`, `application.dispatch.LaunchWorker`, `adapter.home.TimeSettings`, `MachineProfile`.
