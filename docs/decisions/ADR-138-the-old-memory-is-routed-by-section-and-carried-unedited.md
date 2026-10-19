# ADR-138: The old home's memory is routed by section and carried unedited, and the first stow edits it

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 18 splits the old `data/captain.md` into `memory/profile.md` (every machine) and `memory/machine.md` (this machine only) by section (table 18.1), moves `data/captain-shared.md` into the profile, filters `data/learnings.md` (table 18.2) and copies `data/memory-archive.md` to `memory/archive.md`.
Both tables also name edits on the way, such as rewriting the delivery flow once into its current shape.
The tables name the captain's own headings, employer projects included, which tracked code must not hold (ADR-017).
The learnings file has changed since the tables were written: entries were merged, added and re-dated.
The session-start digest is clean only when the three startup files exist (ADR-132), and `edi memory check` fails above the budget or on a heading repeated across them (ADR-120).

**Decision:** `MemoryImport` routes each `##` section of the old captain memory by its heading alone, with three generic rules, and moves its text unedited:
- a heading that opens with the old harness's name concerns the harness EDI replaces, so the section is set aside at the end of `memory/archive.md`;
- a heading that names credentials, secrets, JIRA, timesheets or worklogs, or names one of the old registry's projects other than EDI and the old harness as a whole word, concerns this machine and goes to `memory/machine.md`;
- every other section goes to `memory/profile.md`, followed by every section of the shared captain memory.
The learnings are carried whole, every section in order, and their preamble only when it holds entries; the old archive is carried verbatim.
Each file starts with EDI's own short header instead of the old file's title and preamble, and is written even when the old home has nothing for it, so the digest never lists it missing.
The dry run lists every section with the file it goes to, and the notes tell the captain that the edits of tables 18.1 and 18.2 are the first `/stow`'s work.

**Motivation:** Routing by generic rules keeps the harness person-agnostic, and both startup files load on this machine anyway, so a misrouted section changes nothing until the profile is exported to another machine, by which time the stow has checked it.
Editing a rule's wording is judgement the stow procedure exists for, and the captain reviews it there.

**Alternatives considered:**
- The tables of section 18 as fixed lists in code - would put the captain's headings and employer projects into tracked files, and would miss headings changed since.
- Filtering the learnings by keywords of the old mechanics - entries that must stay name the same scripts, so a keyword filter drops the wrong ones.
- Rewriting the edited sections in code - a text rewrite cannot be checked, and would change the captain's words without the captain seeing it.

**Consequences:**
- Good: nothing of the old memory is lost; set-aside sections stay in the cold tier and the originals in the archive (ADR-140).
- Bad: the first session's stow has more to curate than the specification assumed.

**Revisit when:** A second migration source needs the same split.

**Sources:** EDI rewrite specification, sections 8.8, 18, 18.1 and 18.2; ADR-017, ADR-120, ADR-132; `application.migration.MemoryImport`, `MarkdownSections`, `HomeImportPlanner`; AT-47.
