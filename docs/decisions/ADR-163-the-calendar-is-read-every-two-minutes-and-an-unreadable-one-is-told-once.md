# ADR-163: The calendar is read every two minutes, presence switches from the last reading, and the captain hears once when it cannot be read

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-122 turns presence quiet for a meeting in progress, and ADR-126 read the calendar at most every fifteen minutes, so a meeting could start a quarter of an hour before presence noticed it.
A failed read only wrote a warning to the core's log, once per attempt, and in three days of failures the captain was never told.
The supervision round follows presence every 30 seconds (ADR-077), and each read starts a `powershell.exe` that talks to Outlook for about four seconds (ADR-166).

**Decision:** `MeetingCalendar` keeps the latest `CalendarReading`: the day, when it was read, that day's entries, and an `UnreadableCalendar` when the read failed.
It reads the calendar again two minutes after the latest reading (`CalendarReading.READ_AGAIN_AFTER`) or on a new day, and between readings every round finds a meeting in progress in the entries it already holds, so presence turns quiet in the round a known meeting starts and returns at its end.
A failed read keeps the day's entries it already holds and the moment the calendar first became unreadable, and logs a warning only when the calendar turns unreadable and an information line when it can be read again; `presence.calendarIgnore` still decides which entries never mark a meeting.
`NoticeTheUnreadableCalendar` runs in the supervision round after presence is followed, and shows one Windows notification for each spell of unreadability, in the first round the captain's presence keeps the chat quiet.
While the calendar cannot be read, the captain report carries a line beside the presence, `Calendar: unreadable since ...`, with the failure's own words.

**Motivation:** One read every two minutes notices a meeting added at the last moment within two minutes, and a meeting already on the calendar within a round, at the cost of a short PowerShell every two minutes; a failure the captain sees once, and can read in every report until it clears, is acted on, where a warning repeated in a log is not.

**Alternatives considered:**
- Reading the calendar every round - a PowerShell every 30 seconds for an answer that rarely changes.
- Reading the day once and never again - a meeting accepted during the day would never turn presence quiet.
- A decision card for the unreadable calendar - there is nothing to decide, and a card would stay open after the calendar recovers.
- A notification for every failed read - one every two minutes while the calendar stays unreadable.

**Consequences:**
- Good: AT-46 holds with a meeting noticed within one round, and the captain learns of an unreadable calendar from the report and, while quiet, from one notification.
- Bad: a core restarted while the calendar stays unreadable notifies the captain once more, since the spell is held in memory.

**Revisit when:** Outlook's reads show up in the round's time, or the calendar gets another source.

**Sources:** EDI rewrite specification, section 12.7 (decision E24); ADR-077, ADR-122, ADR-126, ADR-166; `domain.presence.CalendarReading`, `UnreadableCalendar`, `CalendarEntry`, `application.presence.MeetingCalendar`, `NoticeTheUnreadableCalendar`, `CaptainNotices`, `FollowThePresence`, `application.supervision.SupervisionRound`, `web.report.CaptainReport`, `CaptainReportPresenter`, `ReportController`.
