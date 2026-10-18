# ADR-133: The console shows the presence, with Present and Demo buttons

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 12.7: presence is shown on the console and in every report header, and the console shows the current mode with Present and Demo buttons, so the captain can leave quiet or start a demo outside the chat.
The sitrep page reads one document, `edi-sitrep.v1` (ADR-107), and follows the console stream (ADR-109); a console request that changes the fleet carries the console's own header (ADR-105).
Away needs the captain's own words, typed in the chat (ADR-122).

**Decision:** `edi-sitrep.v1` gains `presence` with the mode, whether a demo holds and the presence in plain words (`ConsoleSitrep.PresenceShown`).
The page's header shows the mode, or demo while one holds, with the words as its title, and offers Present when the presence is not present and Demo, or End demo while one holds; each button posts `/api/presence` with the console's header and reads the sitrep again.
The console may choose only present, demo and demo off; quiet and away are refused there and set in the chat.
The captain report heads itself with the presence whenever it is not plainly present.

**Motivation:** The console is where the current state always lives, and the two buttons the captain needs during a meeting are one click away.

**Alternatives considered:**
- Every mode on the console - away needs a mandate in the captain's own typed words, which the chat verifies.

**Consequences:**
- Good: a presence change reaches an open page at once through the stream.
- Bad: the mode is hidden on a phone-width page, where only the buttons remain.

**Revisit when:** The captain wants to go quiet from the console.

**Sources:** EDI rewrite specification, sections 12.7 and 15.3 (decision E24); ADR-105, ADR-107, ADR-109, ADR-122; `web.console.ConsoleSitrep`, `SitrepConsolePresenter`, `SitrepConsoleController`, `ConsoleStream`, `web.presence.PresenceController`, `web.report.CaptainReportPresenter`, `edi-console` `ConsoleHeader`, `SitrepPage`, `ConsoleApi`.
