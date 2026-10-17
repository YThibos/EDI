# ADR-093: The tracker port has three adapters, chosen per project and reachable only where the machine names the tracker

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** `Tracker` is one of the six ports, because two production implementations exist: JIRA on the work laptop and EDI's own keys on the private desktop and for EDI itself (ADR-007, decisions E6 and E7).
Section 14.1 names its operations: find an existing ticket from the work's context, create one (only on the captain's word), key a task, transition a ticket, add it to the current sprint and link a draft to it.
Machine differences are chosen at runtime from the machine profile and the project registry (ADR-006), and tracked files name no employer, host or workflow (ADR-017).

**Decision:** `Tracker` offers `kind`, `findExisting`, `create`, `fill`, `verified`, `transition`, `addToCurrentSprint` and `link`; filling a created ticket is its own call, because section 14.2 fills every field after creation, and `verified` reads a ticket so that a given key is checked before work books on it.
Each registry entry names its tracker with `tracker.kind` (`jira`, `edi` or `none`) beside its `projectKey`; a tracker without a kind is refused rather than guessed.
`Trackers` picks the adapter whose kind the project names, and refuses a project on an external tracker when the machine profile names no such tracker, so a laptop project registered on the desktop says so instead of failing at the first call.
The machine profile's `tracker` section holds everything a JIRA instance differs in: `kind` (`jira` or `none`), `baseUrl` (https, or http on 127.0.0.1 only), `credentials` (the keys file and its section), the `evergreen` tickets, the `workflow` statuses in their forward order, the `mergedStatus` a shipped ticket moves to, the `issueType` of created tickets and the `epicLinkField`; a profile that would leave EDI unable to reach or move a ticket safely is refused when it is read.
`JiraTracker` speaks JIRA's REST API version 2 and its agile API 1.0 through `JiraCalls`, which uses the JDK's HTTP client with the proxy and the exceptions the environment's `https_proxy` and `no_proxy` name (`EnvironmentProxies`), because a native executable does not read those variables by itself.
`EdiNativeTracker` issues the next `<KEY>-<n>` of the project's prefix from the store and keeps no tickets, so it finds, transitions, places and links nothing; `NoTracker` issues no key at all.

**Motivation:** The rules that decide which ticket work books on live in the domain and the use cases, and only the adapters know a tracker's wire format, so the same use cases serve both machines (I-37).

**Alternatives considered:**
- JIRA's own Java client library - a large dependency with reflection that a native image would have to be taught, for the eight calls EDI makes.
- The workflow walk hard-coded to one JIRA instance's status names - would name an employer's workflow in tracked code.
- Inferring the tracker from whether a `projectKey` is present - an EDI-native key and a JIRA key look alike.

**Consequences:**
- Good: a new tracker, such as the future EDI work board, is one adapter and one registry word.
- Bad: the profile must list the workflow's statuses, which the captain keeps in step with the JIRA instance.

**Revisit when:** EDI talks to a second external tracker, or JIRA retires REST version 2.

**Sources:** EDI rewrite specification, sections 5.3, 7.2, 8.4, 14.1, 14.2 and 14.4 (decisions E6, E7); ADR-006, ADR-007, ADR-017; `application.port.Tracker`, `application.tracker.Trackers`, `adapter.tracker.JiraTracker`, `JiraCalls`, `EnvironmentProxies`, `Jql`, `EdiNativeTracker`, `NoTracker`, `adapter.home.TrackerSettings`, `MachineProfile`, `adapter.registry.RegisteredTracker`, `domain.tracker.TrackerKind`, `Workflow`, `examples/edi-home/config/machine.yaml`, `docs/verification/jira.md`, `JiraLiveCheckTest`.
