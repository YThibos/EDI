# ADR-155: EDI shows times in the machine's local time zone and stores them in UTC

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Every time EDI showed the captain was in UTC with a `Z`: the console's header clock read "SD 2026.295 · 09:14Z" while the machine's clock read 11:14.
`Moments` formatted every moment in the captain's texts in UTC, and the console's `stardate` read the hours straight out of the ISO-8601 instant.
The captain asked for the machine's own time, which on a laptop in Belgium is Europe/Brussels with summer time.
The store keeps ISO-8601 instants in UTC (ADR-037), and the event log, the ledgers, the logs and every API answer carry instants, so only rendering is in question.
A few application classes already read `ZoneId.systemDefault()` for the machine's day, so tests could not set the zone, and the sitrep page ran in a browser that may sit in another zone than the core.

**Decision:** Every time EDI shows a person is in the machine's local time zone, and every time it stores or answers with is a UTC instant.
- The `Clock` port names the machine's time zone beside the moment (`Clock.zone`); `SystemClock` reads it from the system, which takes `TZ` and the operating system's zone, and `ManualClock` lets a test set it.
- `ArchitectureRulesTest` refuses `ZoneId.systemDefault` and `TimeZone.getDefault` outside `adapter.clock`, as it refuses reading the time there (ADR-009).
- `Moments` renders a moment as "2026-10-22 11:14 CEST" and a day as the machine's calendar day, in the zone `Clock.zone` names, with the zone's English short name so summer time shows.
- `PresenceWords`, `HealthWords` and `DecisionLines` render through `Moments`, so the captain report, the sitrep text, the return brief, presence lines, `edi decision list`, task detail, worker lists and the primary's digests share it.
- `edi-sitrep.v1` gains `zone`, the machine's zone id, and the console renders every time on the sitrep page in that zone, not the browser's: the header clock as "SD 2026.295 · 11:14 CEST", with the day of the year counted on the machine's calendar.
- `StatusLineText`, `MeetingCalendar`, `OutlookCalendar`, `MemoryQuery`, `PrepareThePrimary` and `SessionStartDigestQuery` take the machine's day and zone from `Clock.zone`.

**Motivation:** The captain reads times against the clock on the wall, so a UTC time is a sum to do and a chance to misread a deadline by one or two hours.
Storing UTC keeps instants comparable and the log unambiguous across a summer time change.
One zone, named by the core, keeps the console and the command in step, and a zone the tests can set makes the summer time boundary testable.

**Alternatives considered:**
- Showing UTC next to the local time - twice the reading for a captain who works in one zone.
- The browser's zone on the sitrep page - the page and the command could disagree when the browser sits elsewhere, so the core names the zone.
- The core rendering the console's times as text - the page keeps its stardate design, and the document keeps instants as every API does.
- A zone set in EDI's configuration - the machine already knows its zone, and a second setting can drift from it.

**Consequences:**
- Good: times read as the captain's own, across the change to and from summer time.
- Good: tests set the zone through `ManualClock`, so no test depends on the zone of the machine that runs it.
- Bad: the console and the core each spell the zone from their own Unicode CLDR data, the page in British English because a browser's plain English writes "GMT+2" for Brussels; they agree for the Western, Central and Eastern European zones and for UTC, but elsewhere the core may write "EDT" where the page writes "GMT-4", at the same wall-clock time.
- Bad: a refusal from the application layer that names an instant, such as `RecoverFromOutage`'s usage limit, still writes it in ISO-8601 UTC, since only the web layer renders moments.

**Revisit when:** The captain works across time zones, or another person reads EDI from another zone.

**Sources:** The captain's request of 22/10/2026 (EDI-33); ADR-009, ADR-037, ADR-107; `application.port.Clock`, `adapter.clock.SystemClock`, `web.text.Moments`, `web.console.ConsoleSitrep`, `edi-console` `stardate.ts`, `ArchitectureRulesTest`.
