# ADR-122: Presence is held by the core, set by the captain and followed every supervision round

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 12.7 and ADR-030: three presence modes, present, quiet and away; no typed prompt for `presence.autoQuietAfter` (15 minutes) switches present to quiet, and so does a real meeting where the calendar is available; silence never switches to away; `/demo` holds present until `/demo off`, the end of the current meeting or three hours; the captain's next typed prompt ends quiet or away; away records the captain's words as the mandate and caps workers at the number running when the captain left (U-07).
AT-46 tests the threshold, silence never becoming away, the calendar filter and the demo hold.

**Decision:** One `presence` row holds the mode, since when, its cause (captain, silence or meeting), away's mandate and worker cap, and a demo's end; `Presence` keeps a mandate exactly in away and a demo only in present.
`ChangePresence` sets it for `edi presence present|quiet|away --mandate|demo [off]` and the console's buttons, and returns it to present at the captain's typed prompt; a demo lasts until the meeting in progress ends, at most three hours.
Away's mandate must be words the captain typed in the exchange, with or without `/afk` (ADR-115), and its worker cap is the number of workers serving at that moment; `AwayLimit` refuses a dispatch at the cap and holds deferred launches.
`FollowThePresence` runs in every supervision round: it ends a demo whose time is up, turns present to quiet for a meeting in progress that began after the captain last typed, and turns present to quiet once the captain has not typed for `presence.autoQuietAfter`, counted from their last typed prompt or the presence's start; nothing but the captain's own words sets away.
`MeetingCalendar` finds a meeting in progress where `CalendarEntry.isAMeeting` holds: timed, under four hours, shown busy, accepted, organised or the captain's own, and not matching `presence.calendarIgnore`.
Settings come from personal.yaml (`presence.autoQuietAfter`, `presence.quietBatch`, `presence.calendarIgnore`) and machine.yaml (`presence.calendar: outlook`); each change publishes `PresenceChanged`, which the console stream announces.

**Motivation:** The rules are domain transitions tested with a controllable clock, and one row read by every surface keeps the chat, the report, the digest and the console in agreement.

**Alternatives considered:**
- Computing presence from the store on every read - a change of mode would have no moment to start a return brief or a batch from.
- Quiet for every meeting entry in progress - would turn quiet again while the captain types during a meeting.

**Consequences:**
- Good: AT-46 holds, and silence can never authorise work.
- Bad: the presence lags a meeting's start by up to a supervision round and the calendar's fifteen-minute freshness.

**Revisit when:** The thresholds prove wrong in daily use, or another calendar source is needed.

**Sources:** EDI rewrite specification, sections 12.7 and 21.3 (U-07), decisions E15 and E24 (AT-46); ADR-030, ADR-115; `domain.presence.Presence`, `PresenceMode`, `PresenceCause`, `Mandate`, `CalendarEntry`, `application.presence.ChangePresence`, `FollowThePresence`, `PresenceQuery`, `MeetingCalendar`, `application.dispatch.AwayLimit`, `application.supervision.SupervisionRound`, `adapter.store.PresenceStore`, `PresenceChanged`, `adapter.home.PresenceSettings`, `PersonalConfiguration`, `WindowsSide`, `web.presence.PresenceController`, `PresenceView`, `edi-cli` `PresenceCommand`.
