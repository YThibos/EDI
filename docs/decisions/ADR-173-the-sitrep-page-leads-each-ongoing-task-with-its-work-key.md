# ADR-173: The sitrep page leads each ongoing task with its work key

**Date:** 23/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The Ongoing block of the sitrep page (ADR-107) showed each task by its title alone, so the captain could not tell which EDI or JIRA key a piece of running work books on without asking.
A task's work key lives on its intake (`Intake.workKey`) and is absent for a task that books on no key.
Some titles already start with their own key, such as a JIRA task titled "DOCK-58: rejected request ...".

**Decision:** `ConsoleSitrep.Ongoing` carries the task's work key as its own `key` field, null for an unkeyed task, and `SitrepConsolePresenter` gives the title with a leading copy of that same key taken off, together with the separator after it (spaces, a colon, a bar or a dash).
The key only counts as leading when it ends where the title's word ends, so `EDI-31` never takes the start off a title beginning with `EDI-310`, and the match ignores case.
The console's `OngoingList` shows the key first in the mono face and the ion colour, then the title; an unkeyed task shows its title alone, with no placeholder.

**Motivation:** A key in its own field lets the page style it apart from the title, and taking a leading copy off the title in the core means the key is never shown twice, whatever the console does.

**Alternatives considered:**
- Gluing the key into the title text in the core - the page could no longer style the key, and every other surface reading the title would inherit the prefix.
- Leaving the key out when the title already starts with it - the same task would show its key styled or unstyled depending on how it was titled.
- Prefixing every block of the page - the captain asked for the Ongoing block only; the other blocks keep their titles until asked.

**Consequences:**
- Good: the captain sees the booked key of every running task at a glance.
- Bad: the Ongoing title on the page can differ from the stored title by its leading key.

**Revisit when:** Another block of the page, or the captain report, should show work keys too.

**Sources:** ADR-107; `web.console.ConsoleSitrep`, `SitrepConsolePresenter`; `edi-console` `OngoingList`, `ongoing-list.html`, `styles.css`.
