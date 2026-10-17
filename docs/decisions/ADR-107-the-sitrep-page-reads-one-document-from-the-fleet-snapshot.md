# ADR-107: The sitrep page reads one document, edi-sitrep.v1, presented from the fleet snapshot

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 15.3: `SitrepConsoleController` serves `GET /api/sitrep`, the fleet snapshot as JSON with the schema `edi-sitrep.v1`, and the page renders a HUD strip (need you, ongoing, landed recently, charted next), the decision deck, ongoing, landed recently and charted next.
Section 5.4 lists what the snapshot carries, among it ongoing tasks with their worker and pipeline state and recently landed work; ADR-012 makes every surface a presenter over one query and accepts that the snapshot must carry what any presenter needs.
An answered card must keep showing the captain's verbatim answer after a reload (section 15.3), and fleet-sized data must stay small enough for any surface (I-36).

**Decision:** `FleetSnapshot` gains three parts, read in the same transaction: each ongoing task as an `OngoingTask` with its latest worker, pipeline run and delivery; the ships and scouts done in the last seven days as `LandedTask` with their delivery, a captain item answered into done being one of the day's answers instead; and the decisions answered in the last 24 hours as `RecentAnswer`.
`SitrepConsolePresenter` turns the snapshot into `ConsoleSitrep`: the open decision cards; the recent answers; each ongoing task with a state (its worker's last status, a resolved status shown as working, its activity before any status, or `waiting` without a worker) and what it is doing (the running pipeline step, else the status text); at most ten landed tasks and twenty charted tasks, each with the total, so a 2 MB backlog gives a document of a few kilobytes (AT-36).
Charted next holds the queued and the parked tasks together, newest filed first, a parked one with the reason the text report gives (`ParkedTask.whyParked`, moved there so both presenters share it).
The document also names the machine (`MachineProfile.name`) and the captain's address word (`PersonalConfiguration.address`), and carries the moment it was generated, which the page shows as a stardate.
The captain report reads only the parts it read before, so its output is unchanged byte for byte.

**Motivation:** One document per page read keeps the page a pure view, and the windows keep it small while still answering what landed this week and what the captain answered today.

**Alternatives considered:**
- A second query for the console - two views of the fleet could disagree, which ADR-012 rules out.
- Showing the whole backlog on the page - the document would grow with the backlog, against I-36.
- Judging each worker's health live with tmux on every read - the page re-reads on every event, and the supervision round already records deaths in the store.

**Consequences:**
- Good: the deck can show today's answers as answered cards from the store, so an answer never silently disappears.
- Bad: the snapshot read now touches the worker, pipeline and delivery stores for each ongoing task.

**Revisit when:** The work board needs the full queue, or a presenter needs live worker health.

**Sources:** EDI rewrite specification, sections 5.4, 15.3 and I-36; ADR-012, ADR-046; `application.fleet.FleetSnapshotQuery`, `FleetSnapshot`, `OngoingTask`, `LandedTask`, `RecentAnswer`, `ParkedTask`, `web.console.SitrepConsoleController`, `SitrepConsolePresenter`, `ConsoleSitrep`, `invariant.MemoryAndCraftTest` (AT-36).
