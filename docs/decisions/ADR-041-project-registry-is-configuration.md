# ADR-041: The project registry is configuration, read on every use

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The project registry, `$EDI_HOME/config/projects.yaml`, holds each project's machine-specific settings: its clone, forge, account, base branch, delivery mode, tracker and craft rules (section 8.4).
Slice S1 needs only a project's name, to check the projects a task names, and its project key, to show its work keys; the other fields are read by the slices that use them.
The specification also lists a `project` table, whose only state is the per-project sequence of EDI-native work keys (section 8.5), which the trackers of slice S6 issue.

**Decision:** `ProjectRegistry` reads `projects.yaml` with Jackson's YAML module on every use and maps each entry to `Project`, a name and its project key from `tracker.projectKey`; it ignores every other field, so the file keeps the full shape of section 8.4 and later slices add the fields they read.
An absent or empty file registers no project; an unreadable file or an unusable name is refused as `UnreadableRegistry`, naming the file.
`edi project list` shows the registry; adding and changing projects comes with the slices that need their clone and forge.
The store has no `project` table until slice S6 issues work keys from it.

**Motivation:** The registry stays a file the captain can read and edit, with one source for every project setting, and nothing in S1 models a field before its first reader.

**Alternatives considered:**
- The registry as a store table - a second source for settings that belong to the machine's configuration.
- Reading the file once at start-up - an edit would need a restart.

**Consequences:**
- Good: an edit to the registry takes effect at the next command.
- Bad: each command that checks projects reads and parses a small file.

**Revisit when:** Reading the file shows up in the command's round trip, or a project setting must change transactionally with the store.

**Sources:** EDI rewrite specification, sections 7.1, 8.2, 8.4 and 8.5; `adapter.registry.ProjectRegistry`, `RegisteredProject`, `examples/edi-home/config/projects.yaml`.
