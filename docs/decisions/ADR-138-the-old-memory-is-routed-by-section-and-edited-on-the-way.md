# ADR-138: The old home's memory is routed by section and edited on the way, as tables 18.1 and 18.2 name

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 18 splits the old `data/captain.md` into `memory/profile.md` (every machine) and `memory/machine.md` (this machine only) by section (table 18.1), moves `data/captain-shared.md` into the profile, filters `data/learnings.md` (table 18.2) and copies `data/memory-archive.md` to `memory/archive.md`.
Both tables also name edits on the way, such as pointing the global rules at the machine profile and dropping the old harness's own mechanics from the learnings.
The tables name the captain's own headings, employer projects included, which tracked code must not hold (ADR-017).
The learnings file has changed since the tables were written: entries were merged, added and re-dated.
The session-start digest is clean only when the three startup files exist (ADR-132), and `edi memory check` fails above the budget or on a heading repeated across them (ADR-120).

**Decision:** `MemoryImport` routes each `##` section of the old captain memory by its heading alone, with three generic rules:
- a heading that opens with the old harness's name concerns the harness EDI replaces, so the section is set aside at the end of `memory/archive.md`;
- a heading that names credentials, secrets, JIRA, timesheets or worklogs, or names one of the old registry's projects other than EDI and the old harness as a whole word, concerns this machine and goes to `memory/machine.md`;
- every other section goes to `memory/profile.md`, followed by every section of the shared captain memory.
On the way, `CaptainMemoryEdits` makes the edits of table 18.1, each keyed on the opening words of a section's heading:
- the global preferences pointer names the file the machine profile holds as `captain.globalRules` instead of a path;
- the hard commit rules key every branch on the task's work key instead of JIRA alone, and drop the lines about the old harness's branch-prefix script;
- the delivery flow drops the base branch per project, which `projects.yaml` holds, and folds the reviewer's removal into one statement;
- applying changes locally names the checkout root from the machine profile instead of a path;
- the reporting format points at `edi report` instead of the old harness's script.
`LearningsFilter` applies table 18.2 to every entry of the learnings, preamble included when it holds entries:
- an entry whose title names the old harness's own mechanics, such as its command guard, tasks-axi, its own tests, its self-restarting monitoring, its quietening of parked workers, rejoining an existing copy, its fleet lock or one of its `fm-` scripts, is dropped;
- the `gh-axi` entry is generalised to always passing `--repo`, and the top-level `cd` entry keeps only its `cmd.exe` fact;
- every other entry is carried as written.
The old archive is carried verbatim.
Each file starts with EDI's own short header instead of the old file's title and preamble, and is written even when the old home has nothing for it, so the digest never lists it missing.
The dry run lists every section with the file it goes to, and how many learnings the filter dropped.

**Motivation:** Routing by generic rules keeps the harness person-agnostic, and both startup files load on this machine anyway, so a misrouted section changes nothing until the profile is exported to another machine.
The edits are a step of the specified migration, so the import makes them instead of leaving the first session's stow to find them.
Each edit and filter rule names the old harness's mechanics or EDI's own settings, never the captain's words, so the tracked code stays person-agnostic.

**Alternatives considered:**
- The tables of section 18 as fixed lists of the captain's headings and entries in code - would put the captain's headings and employer projects into tracked files, and would miss headings changed since.
- Leaving the edits and the filter to the first `/stow` - a migration step the specification assigns to the import would depend on a later session's judgement.
- Rewriting whole sections into new text - would replace the captain's words wholesale, where each named edit changes only what the table names.

**Consequences:**
- Good: the old harness's mechanics do not reach EDI's startup memory, and the originals stay in the archive (ADR-140).
- Bad: an edit whose opening words or marker no longer appear in the old file leaves that section or entry as written, so the stow still reviews what the import carried.

**Revisit when:** A second migration source needs the same split.

**Sources:** EDI rewrite specification, sections 8.8, 18, 18.1 and 18.2; ADR-017, ADR-120, ADR-132; `application.migration.MemoryImport`, `CaptainMemoryEdits`, `LearningsFilter`, `ListEntries`, `MarkdownSections`, `HomeImportPlanner`; AT-47.
