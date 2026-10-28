# The EDI backlog

Every EDI upgrade and patch that is queued or held is a self-contained work item here, so a Claude Code cloud session on this repository, with nothing but the repository, can pick one up and deliver it to the same standard as an EDI worker.
This folder is a plan, not a decision: the decision log holds why the backlog lives in the repository ([backlog-in-the-repository](../decisions/backlog-in-the-repository.md)), and this folder holds what to build.

## What is here

- `README.md`: this index and the pickup protocol.
- `EDI-<n>.md`: one file per item, the full and canonical spec: goal, context, acceptance criteria, the decisions and answers that bind it restated as requirements, the research it rests on, what to read first and how to verify.
- `research/`: the research reports the specs rest on, carried over in full where a worker needs them: [the lifecycle, plans and report plan](research/EDI-74-plan.md), [the console and week-one plan](research/EDI-52-plan.md), [the measurements](research/EDI-75-measurements.md) and [the validation pipeline research](research/EDI-105-validation-pipeline.md).

Each item file is the only source of truth for its item.
A GitHub issue per item tracks the claim, the questions and the link between issue, branch and pull request; it copies no criteria.

## How to read an item

1. Read [AGENTS.md](../../AGENTS.md) first: it is the standard every change meets, and `CLAUDE.md` is the same file.
2. Read the item's file top to bottom, then every record and file under its "Read first".
3. The records in [docs/decisions](../decisions/README.md) explain why EDI is built as it is; read the ones the item names before changing the code they govern.
4. A path or line number in a research report is as the code stood on the day of writing: confirm it against the current code.
5. A ship changes the repository and ends in a pull request; a scout researches and ends in a report, which is also delivered as a pull request on a `research/` branch.
6. Several items refer to decision numbers such as D43 and to invariant numbers such as I-14 and AT-42: the invariants are the nested classes of the invariant catalogue in `edi-core/src/test/java/io/github/ythibos/edi/invariant/` and [invariant-catalogue](../decisions/invariant-catalogue.md); a decision number only names an answer the captain gave, and the item restates it as a requirement.

## Pickup protocol

A session takes one item at a time, in this order.

1. Fetch `origin/main` and read the index below.
2. Pick the first item, in index order, whose status is `open`, whose blockers are all merged into `main`, and that has no open pull request and no claim on its issue.
   Skip every item that is held, parked, in flight or needs the captain's machine; the status column says which.
   Until the issues exist, an open pull request or a pushed branch for the item is the claim.
3. Claim it before doing any work:
   1. comment on the item's issue that you are taking it, once the issues exist;
   2. create the branch `feature/EDI-<n>`, `chore/EDI-<n>` or `research/EDI-<n>`, as the item's header names, from a fresh `origin/main`; never use the platform's default branch name, never `fm/` or `edi/`, and never stack on another unmerged branch;
   3. push it and open a draft pull request early, titled `EDI-<n> <the item's title>`, whose body names the item, links its file and, once the issue exists, says `Closes #<issue>`; opening it first is what keeps another session from taking the same item.
4. Build it as [AGENTS.md](../../AGENTS.md) says:
   - every commit subject starts with the work key, for example `EDI-81 Name the ticket a learning waits on`;
   - no trailer of any kind: no `Co-authored-by`, no `Signed-off-by` naming a model or tool, no "Generated with" line and no session link, in a commit or in the pull request body; the platform may add one by default, so read each commit message and the pull request body before pushing;
   - no `-SNAPSHOT` version anywhere;
   - never push to `main`, never force-push, never merge into `main`; the captain merges;
   - when `origin/main` has moved, merge it into the branch with a merge commit and never rebase;
   - code carries no explanatory comments, tests are fast and deterministic, and writing is United Kingdom English with a plain `-`, one sentence per line in Markdown.
5. Verify locally, as [AGENTS.md](../../AGENTS.md) says, and put the results in the pull request body:
   - `./mvnw verify` before every commit that is meant to be delivered, and `./mvnw test -Pfast` while working;
   - `./mvnw -Pnative,quick-build verify` when the change can reach the native image (`NativeImageInput` in [local-native-builds](../decisions/local-native-builds.md) lists what can), and otherwise one line saying why it was not needed;
   - the item's own "How to verify".
   GitHub Actions does not run on this repository, so no check will appear on the pull request: the body carries the commands run and their results.
   A step the sandbox cannot run, such as the native build or a browser check, is said plainly with the reason; never write that it passed when it was not run.
6. When the local verification is green, update the item: change its status in its file header and its row in the index below to `delivered`, in the same pull request, and mark the pull request ready for review.
7. Stop.
   The captain's primary session reads, merges and processes the pull request; a session never merges.
   A session may start the next open item only when its blockers are already merged, on a new branch from a fresh `origin/main`.

## Open questions

A session never guesses an ambiguous or scope-changing call.

- A question before any code goes on the item's issue, or in the draft pull request's description until the issues exist; a question about code already written goes on the pull request.
- It carries lettered options and a recommendation, each option on its own line, with the recommended one marked as such and the reason given.
- The session then stops until the captain answers there; it never continues on its own recommendation.
- When an answer changes the item, the item's file is updated in the same pull request, so the file stays the single source of truth.
- Several items name questions to ask before code; ask them first.

## What a cloud session does not have

- No `edi` command, no EDI home, no store, no memory and no skills outside this repository: do not run `edi publish-check`; instead read your own diff for anything that names a particular person, employer, account, host or machine path, and any secret, and say in the pull request that the publish check was not run.
- The captain's primary session runs `edi publish-check` before it merges.
- No secrets: never ask for, read or commit one; an item that needs one is marked as needing the captain's machine.
- The pinned toolchain comes from `mise install` ([AGENTS.md](../../AGENTS.md)); if the sandbox cannot install it, say so in the pull request instead of skipping the verification silently.
- Tracked files never name a particular person, employer, account, host or machine path; use invented names in tests and records ([machine-and-person-specifics](../decisions/machine-and-person-specifics.md)).

## Statuses

| Status | Meaning |
|---|---|
| `open` | ready to pick up once its blockers are merged |
| `delivered` | its pull request is ready for review or merged |
| `held` | the captain asked to wait; skip it until the captain releases it on its issue and changes its status to `open` |
| `in-flight` | running on the captain's machine when the backlog was written; skip it if its pull request exists or has merged |
| `parked` | waiting for a named event, given in its reason; skip it until that event and the captain's word |
| `needs-captain-machine` | cannot run without the captain's machine, for the reason given; not for a cloud session |

## Issues

One GitHub issue per open or held item, opened after the pull request that adds this folder is merged, so the issues link to files on `main`.

- Title: `EDI-<n> <the item's title>`.
- Body: a short line linking the item's file on `main`, the blockers as issue references, and nothing else; the criteria stay in the file.
- Labels: `wave-1` to `wave-6` (the security findings carry `security` instead), `cloud-ok` or `needs-captain-machine`, and `parked` where an item is held or parked.
- The index below names each item's issue number once the issues exist.
- A session claims an item by commenting on its issue; the pull request's `Closes #<issue>` links issue, branch and pull request, and GitHub closes the issue when the pull request merges.

## The index

Order follows the waves the captain set.
The waves set the order, and an item may start once every item in its blockers column has merged, even while an earlier wave is still open.
Wave 5 is the captain's console plan and report chain, which starts once EDI-89 (the plan dispatches its slices) has merged, so every wave 5 item lists EDI-89 or an item that itself waits for it.
Sizes: XS under two hours, S about half a day, M about a day, L several days of research.
The Issue column shows `-` until the issues are opened.

### Wave 1: no wait

| Order | Item | Title | Size | Blocked by | Status | Issue |
|---|---|---|---|---|---|---|
| 1 | [EDI-81](EDI-81.md) | Learnings name their ticket | S | - | open | - |
| 2 | [EDI-75](EDI-75.md) | Spec first for features with several slices | S | - | open | - |
| 3 | [EDI-94](EDI-94.md) | Worklogs and stories through the tracker | M | - | open | - |
| 4 | [EDI-118](EDI-118.md) | The weekly upkeep flags a newer JDK GraalVM supports and every version Renovate misses, and Renovate runs again | M | - | open | - |

### Wave 2: after the foundations of wave 1 and the lifecycle domain

| Order | Item | Title | Size | Blocked by | Status | Issue |
|---|---|---|---|---|---|---|
| 5 | [EDI-85](EDI-85.md) | The stage is stored | M | - | open | - |
| 6 | [EDI-83](EDI-83.md) | The anchor as an index | S | EDI-81 | open | - |

### Wave 3: after the stage is stored

| Order | Item | Title | Size | Blocked by | Status | Issue |
|---|---|---|---|---|---|---|
| 7 | [EDI-88](EDI-88.md) | Plans and their waits | M | EDI-85 | open | - |
| 8 | [EDI-86](EDI-86.md) | Delivered, changing, ready | M | EDI-85 | open | - |
| 9 | [EDI-70](EDI-70.md) | Landing by the merged head: squash-merge cleanup | M | EDI-85 | open | - |
| 10 | [EDI-87](EDI-87.md) | The reasons a task waits, as a set | M | EDI-85 | open | - |

### Wave 4: plans dispatch themselves

| Order | Item | Title | Size | Blocked by | Status | Issue |
|---|---|---|---|---|---|---|
| 11 | [EDI-89](EDI-89.md) | The plan dispatches its slices | M | EDI-88 | open | - |
| 12 | [EDI-71](EDI-71.md) | One effort word per plan | S | EDI-88 | open | - |
| 13 | [EDI-65](EDI-65.md) | The page, the report and the digest on stages: report ready, not ongoing | M | EDI-86, EDI-70 | open | - |
| 14 | [EDI-67](EDI-67.md) | The base moved: open requests stay mergeable | S | EDI-86 | open | - |

### Wave 5: the console plan and the report, once plans dispatch themselves

| Order | Item | Title | Size | Blocked by | Status | Issue |
|---|---|---|---|---|---|---|
| 15 | [EDI-90](EDI-90.md) | The captain's last look and the delta | S | EDI-65 | open | - |
| 16 | [EDI-91](EDI-91.md) | The report's new layout | M | EDI-90 | open | - |
| 17 | [EDI-92](EDI-92.md) | The stop hook's new rule | S | EDI-90 | open | - |
| 18 | [EDI-55](EDI-55.md) | Quiet delivers the primary's own notifications at once | S | EDI-89 | open | - |
| 19 | [EDI-56](EDI-56.md) | autoQuietAfter accepts PT0S | XS | EDI-89 | open | - |
| 20 | [EDI-57](EDI-57.md) | Link chips on every row | M | EDI-89 | open | - |
| 21 | [EDI-58](EDI-58.md) | View buttons in the header | S | EDI-89 | open | - |
| 22 | [EDI-59](EDI-59.md) | The task panel | M | EDI-57, EDI-58 | open | - |
| 23 | [EDI-60](EDI-60.md) | Live check-in on a worker | M | EDI-59 | open | - |
| 24 | [EDI-61](EDI-61.md) | The Fleet view | M | EDI-58, EDI-60 | open | - |
| 25 | [EDI-62](EDI-62.md) | The Week view | S | EDI-58 | open | - |
| 26 | [EDI-63](EDI-63.md) | Tab title and browser notification | S | EDI-89 | open | - |
| 27 | [EDI-64](EDI-64.md) | Quiet and away buttons | S | EDI-89 | open | - |
| 28 | [EDI-66](EDI-66.md) | edi board show | S | EDI-59 | open | - |
| 29 | [EDI-68](EDI-68.md) | edi park and edi resume | M | EDI-87, EDI-89 | open | - |
| 30 | [EDI-69](EDI-69.md) | A standing CI note per project | S | EDI-89 | open | - |
| 31 | [EDI-72](EDI-72.md) | Spike: a steer at the next tool call | S | EDI-89 | needs-captain-machine: the spike needs a real Claude Code worker session in a private tmux socket and a real long turn, which only the captain's machine gives. | - |
| 32 | [EDI-73](EDI-73.md) | EDI's concept art in the repository | S | EDI-89 | needs-captain-machine: the two source images exist only on the captain's machine and are not in the repository. | - |
| 33 | [EDI-93](EDI-93.md) | Plans on the page and in the report | S | EDI-89, EDI-91, EDI-59 | open | - |
| 34 | [EDI-96](EDI-96.md) | Ship-raised decisions batched into console rounds | M | EDI-59 | open | - |

### Wave 6: after the Week view and a week of use

| Order | Item | Title | Size | Blocked by | Status | Issue |
|---|---|---|---|---|---|---|
| 35 | [EDI-97](EDI-97.md) | Effort and review yield recorded per task, shown in the Week view | M | EDI-62 | open | - |
| 36 | [EDI-76](EDI-76.md) | Introspect again after a week of the EDI-52 and EDI-74 changes | M | EDI-93 | needs-captain-machine: the measurement reads the primary's transcripts and EDI's store, which exist only on the captain's machine. | - |

### Security findings, to land before the repository is made public

The findings of the read-only security review of 28 October 2026, held on the captain's machine for the cloud session.
The four worker-escape items come first, then the pinned actions the captain named alongside them, then the low findings.
Each must land before the repository is made public ([EDI-132](EDI-132.md)), which waits on the four worker-escape items and the pinned actions.
The section sits after the six waves; the captain may move it up.

| Order | Item | Title | Size | Severity | Blocked by | Status | Issue |
|---|---|---|---|---|---|---|---|
| 37 | [EDI-122](EDI-122.md) | A worker can never act as the primary by dropping its token file | S | medium | - | open, before EDI-132 | - |
| 38 | [EDI-123](EDI-123.md) | A worker can never type into the captain's primary session or reach the edi tmux socket another way | M | medium | - | open, before EDI-132 | - |
| 39 | [EDI-124](EDI-124.md) | The shell secret guard matches paths, not the literal text of a word | M | medium | - | open, before EDI-132 | - |
| 40 | [EDI-125](EDI-125.md) | A worker cannot switch its own hooks off through local Claude Code settings | S | medium, unverified | - | open, before EDI-132 | - |
| 41 | [EDI-131](EDI-131.md) | CI actions are pinned to commit SHAs | XS | info | - | open, before EDI-132 | - |
| 42 | [EDI-126](EDI-126.md) | The console checks the Host header and refuses to be framed | S | low | - | open, before EDI-132 | - |
| 43 | [EDI-127](EDI-127.md) | The console's sign-in link never appears on a process command line | S | low | - | open, before EDI-132 | - |
| 44 | [EDI-128](EDI-128.md) | Shell snippets built from configuration validate what they interpolate | S | low | - | open, before EDI-132 | - |
| 45 | [EDI-129](EDI-129.md) | The EDI home and its store are readable by the captain only | S | low | - | open, before EDI-132 | - |
| 46 | [EDI-130](EDI-130.md) | The publish-check endpoint only checks paths EDI owns | S | low | - | open, before EDI-132 | - |

### Held, parked and needs the captain's machine, no wave

| Order | Item | Title | Size | Blocked by | Status | Issue |
|---|---|---|---|---|---|---|
| - | [EDI-110](EDI-110.md) | The console header hides its stardate and machine slots while the core does not answer | XS | - | held: backlogged on the captain's word, to start when he says so. | - |
| - | [EDI-111](EDI-111.md) | An adopted task's title drops the old harness's kind prefix | XS | - | held: backlogged on the captain's word, to start when he says so. | - |
| - | [EDI-112](EDI-112.md) | Show the full session name in the status bar, and give EDI release versions | M | - | held: backlogged on the captain's word, to start when he says so. | - |
| - | [EDI-113](EDI-113.md) | Guard Grep against the secret files, as Read is | S | - | held: backlogged on the captain's word, to start when he says so. | - |
| - | [EDI-114](EDI-114.md) | Native builds use a fixed share of the cores, so two can run side by side | M | - | held: until the captain's word, and the before and after measurements need the captain's laptop. | - |
| - | [EDI-109](EDI-109.md) | Worker builds get NVD_API_KEY from the captain's secrets file at call time | M | - | needs-captain-machine: the feature reads a secrets file that exists only on the captain's machine; it is held until his word. | - |
| - | [EDI-1](EDI-1.md) | Clear the high-severity vite advisory in the console's lockfile | XS | - | needs-captain-machine: it waits on an upstream release, and the check needs the captain's machine. | - |
| - | [EDI-105](EDI-105.md) | Bring the validation pipeline into EDI's own code flow, replacing no-mistakes | L | EDI-89 | parked: until EDI-89 (EDI-74 step 18) lands, as the captain set on 28 October 2026. | - |
| - | [EDI-120](EDI-120.md) | WaitControllerTest no longer races its own wake, and no test opens a real browser | S | - | in-flight: on the captain's machine, a worker is delivering it; not for the cloud session, skip it if its pull request exists or has merged | - |
| - | [EDI-133](EDI-133.md) | The turn report shows only what needs the captain; the sitrep keeps the overview | M | - | in-flight: on the captain's machine, a worker is delivering it; not for the cloud session, skip it if its pull request exists or has merged | - |
| - | [EDI-134](EDI-134.md) | edi merged reports the cleanup once the copy is gone, never a half-removed copy as unlanded work | S | - | held: for the cloud session once the captain changes its status to `open`. | - |
| - | [EDI-132](EDI-132.md) | The EDI repository cleaned of every personal and employer trace and made public | L | EDI-122, EDI-123, EDI-124, EDI-125, EDI-131 | needs-captain-machine: the switch of the default branch, the deletions and the settings are on the captain's account; held on the captain's word, after the four worker-escape fixes and the pinned actions. | - |

## Not in the backlog

- Work on other projects (the integration, container, branding and notification items of registered projects) belongs to those projects, not to this repository.
- Work already delivered is in git history and the decision records, not here.
