# EDI shows times in the machine's local time zone and stores them in UTC

**Kind:** Design

## Context

Every time EDI showed was in UTC with a `Z`: the console's clock read 09:14Z while the machine's clock read 11:14, so the captain had to do sums and could misread a deadline by an hour or two.
The store keeps ISO-8601 instants in UTC ([store](store.md)), and the event log, the ledgers, the logs and every API answer carry instants.
A few classes read `ZoneId.systemDefault()` directly, so tests could not set the zone, and the sitrep page runs in a browser that may sit in another zone than the core.

## Decision

Every time EDI shows a person is in the machine's local time zone, and every time it stores or answers with is a UTC instant.
The `Clock` port names the machine's zone beside the moment (`Clock.zone`): `SystemClock` reads it from the system, `ManualClock` lets a test set it, and `ArchitectureRulesTest` refuses `ZoneId.systemDefault` and `TimeZone.getDefault` outside `adapter.clock`.
`Moments` renders a moment as "2026-10-22 11:14 CEST" and a day as the machine's calendar day, with the zone's English short name so summer time shows, and every text presenter renders through it.
`edi-sitrep.v1` carries `zone`, the machine's zone id, and the console renders every time on the sitrep page in that zone, not the browser's; on a screen 350 CSS pixels wide or narrower the header clock keeps only the time.
Every rule that needs the machine's day, such as the calendar, the status line or the weekly gardening round, takes it from `Clock.zone`.

## Why

The captain reads times against the clock on the wall, while UTC keeps stored instants comparable and the log unambiguous across a summer time change.
One zone, named by the core, keeps the console and the command in step, and a zone the tests can set makes the summer time boundary testable.

## Alternatives rejected

- UTC beside the local time - twice the reading for a captain who works in one zone.
- The browser's zone on the sitrep page - the page and the command could disagree.
- A zone in EDI's configuration - the machine already knows its zone, and a second setting can drift.

## Consequences

- Good: times read as the captain's own across summer time, and no test depends on the zone of the machine that runs it.
- Bad: the core and the browser spell some zones differently, such as "EDT" against "GMT-4", outside the European zones and UTC.
- Bad: refusals from the application layer still write instants in UTC, and a hold until a day still falls due at midnight UTC; this changes what EDI shows, not when it acts.

## When to revisit

When the captain works across time zones, or another person reads EDI from another zone.

## How we got here

- 22/10/2026: the captain asked for the machine's own time instead of UTC (EDI-33).
