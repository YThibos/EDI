# The EDI backlog

Every EDI upgrade and patch that is queued or held is a self-contained work item here, so a Claude Code cloud session on this repository, with nothing but the repository, can pick one up and deliver it to the same standard as an EDI worker.
This folder is a plan, not a decision: the decision log holds why the backlog lives in the repository ([backlog-in-the-repository](../decisions/backlog-in-the-repository.md)), and this folder holds what to build.

## What is here

- `README.md`: this index and the pickup protocol.
- `board.mjs`: prints the index with each item's status and issue, read from the item files; `node docs/backlog/board.mjs --next` names the items a session may pick up.
- `EDI-<n>.md`: one file per item, the full and canonical spec: goal, context, acceptance criteria, the decisions and answers that bind it restated as requirements, the research it rests on, what to read first and how to verify.
- `research/`: the research reports the specs rest on, carried over in full where a worker needs them: [the lifecycle, plans and report plan](research/EDI-74-plan.md), [the console and week-one plan](research/EDI-52-plan.md), [the measurements](research/EDI-75-measurements.md) and [the validation pipeline research](research/EDI-105-validation-pipeline.md).

Each item file is the only source of truth for its item, its status and its issue included: the index holds only what rarely changes, the order, the title, the size and the blockers, so delivering an item edits its own file and nothing another pull request edits.
A GitHub issue per item tracks the claim, the questions and the link between issue, branch and pull request; it copies no criteria.
The captain's primary session adds an item with its [edi-backlog-item](../../claude/skills/edi-backlog-item/SKILL.md) skill, in one step: the item's file, its row in the index, the pull request that adds both, the item's issue, named in the item's file, and the hold on the local task, so the captain's machine never dispatches it.

## How to read an item

1. Read [AGENTS.md](../../AGENTS.md) first: it is the standard every change meets, and `CLAUDE.md` is the same file.
2. Read the item's file top to bottom, then every record and file under its "Read first".
3. The records in [docs/decisions](../decisions/README.md) explain why EDI is built as it is; read the ones the item names before changing the code they govern.
4. A path or line number in a research report is as the code stood on the day of writing: confirm it against the current code.
5. A ship changes the repository and ends in a pull request; a scout researches and ends in a report, which is also delivered as a pull request on a `research/` branch.
6. Several items refer to decision numbers such as D43 and to invariant numbers such as I-14 and AT-42: the invariants are the nested classes of the invariant catalogue in `edi-core/src/test/java/io/github/ythibos/edi/invariant/` and [invariant-catalogue](../decisions/invariant-catalogue.md); a decision number only names an answer the captain gave, and the item restates it as a requirement.

## Pickup protocol

A session takes one item at a time, in this order.

1. Fetch `origin/main` and run `node docs/backlog/board.mjs`, which prints the index below with each item's status and issue from its file.
2. Pick the first item, in index order, whose status is `open`, whose blockers are all merged into `main`, and that has no open pull request and no claim on its issue; `node docs/backlog/board.mjs --next` lists the open items whose blockers are all delivered.
   Skip every item that is held, parked, in flight or needs the captain's machine; the item's status says which.
   Until the issues exist, an open pull request or a pushed branch for the item is the claim.
   An item whose status says "continue the draft pull request" was handed over by the captain's machine: that draft is not a claim; continue it on its own branch, read its description and the item's Handover section first, and ask its open questions before any code.
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
   GitHub Actions runs the CI workflow on every pull request; the body still carries the commands run locally and their results, and names the CI run it waits on.
   A step the sandbox cannot run, such as the native build or a browser check, is said plainly with the reason; never write that it passed when it was not run.
6. When the local verification is green, update the item: change its status in its file header to `delivered`, in the same pull request, and mark the pull request ready for review once its CI run on the last pushed head is green; the index below carries no status, so a delivery never edits it.
   A CI failure is the session's to fix, on that pull request, even when its cause came in on `main`; say in the body what failed and why.
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

An item's status is the `Status` field in its file's header, and nowhere else; `node docs/backlog/board.mjs` shows them all beside the index.

| Status | Meaning |
|---|---|
| `open` | ready to pick up once its blockers are merged |
| `delivered` | its pull request is ready for review or merged |
| `held` | the captain asked to wait; skip it until the captain releases it on its issue and changes its status to `open` |
| `in-flight` | running on the captain's machine when the backlog was written; skip it if its pull request exists or has merged |
| `parked` | waiting for a named event, given in its reason; skip it until that event and the captain's word |
| `needs-captain-machine` | cannot run without the captain's machine, for the reason given; not for a cloud session |
| `folded` | its criteria were moved into the item it names, which delivers them; skip it |
| `dropped` | the captain dropped it, for the reason given; skip it |

## Issues

One GitHub issue per open or held item.
The [edi-backlog-item](../../claude/skills/edi-backlog-item/SKILL.md) skill opens it together with the pull request that adds the item, and names it in the item's file, in its `Issue` field, in the same pull request; its link to the item's file on `main` works once that pull request merges.

- Title: `EDI-<n> <the item's title>`.
- Body: a short line linking the item's file on `main`, the blockers as issue references, and nothing else; the criteria stay in the file.
- Labels: `wave-1` to `wave-6` (the security findings carry `security` instead), `cloud-ok` or `needs-captain-machine`, and `parked` where an item is held or parked; the skill creates a missing label.
- The item's file names its issue once the issue exists, and `-` before.
- A session claims an item by commenting on its issue; the pull request's `Closes #<issue>` links issue, branch and pull request, and GitHub closes the issue when the pull request merges.

## The index

Order follows the waves the captain set.
An item added later goes at the end of its section's table and takes the highest order plus one in the whole index, on `main` and on every branch awaiting merge, whatever its section, so every order stays unique and no other row changes; an item in the held section carries `-`, as its rows do.
The waves set the order, and an item may start once every item in its blockers column has merged, even while an earlier wave is still open.
Wave 5 is the captain's console plan and report chain, which starts once EDI-89 (the plan dispatches its slices) has merged, so every wave 5 item lists EDI-89 or an item that itself waits for it.
On 3 November 2026 the captain lifted that wait for the slices that need neither plans nor stages, EDI-55, EDI-56, EDI-57, EDI-58, EDI-63, EDI-64 and EDI-66, which now wait on nothing.
Sizes: XS under two hours, S about half a day, M about a day, L several days of research.

### Wave 1: no wait

| Order | Item | Title | Size | Blocked by |
|---|---|---|---|---|
| 1 | [EDI-81](EDI-81.md) | Learnings name their ticket | S | - |
| 2 | [EDI-75](EDI-75.md) | Spec first for features with several slices | S | - |
| 3 | [EDI-94](EDI-94.md) | Worklogs and stories through the tracker | M | - |
| 4 | [EDI-118](EDI-118.md) | The weekly upkeep flags a newer JDK GraalVM supports and every version Renovate misses | M | - |
| 47 | [EDI-143](EDI-143.md) | Every state-changing edi command answers --json on the native core | S | - |
| 52 | [EDI-151](EDI-151.md) | Stopping a worker also ends the processes it left behind in its copy | S | - |
| 53 | [EDI-152](EDI-152.md) | The primary has exactly one live edi wait, and a wait that goes nowhere is refused | S | - |
| 54 | [EDI-153](EDI-153.md) | An unanswered worker question comes back to the primary until it is answered | S | - |
| 55 | [EDI-154](EDI-154.md) | The turn report shows every worker waiting on the primary | XS | - |
| 56 | [EDI-155](EDI-155.md) | An introspect skill turns a failure of mine into backlogged hard rules, its evidence gathered by a script | M | - |
| 60 | [EDI-162](EDI-162.md) | edi worker show prints a worker's full last status and its open question | XS | - |
| 62 | [EDI-164](EDI-164.md) | Every rule, guard and hook records when it fires, and the core records its own health | M | EDI-132 |
| 63 | [EDI-165](EDI-165.md) | A weekly gardening round measures slips and rules and drafts hard rules on a board | M | EDI-155, EDI-164 |
| 64 | [EDI-166](EDI-166.md) | Sparks are captured in a second and sorted at gardening | S | EDI-132 |
| 65 | [EDI-167](EDI-167.md) | The SNAPSHOT check accepts a new module's parent reference to its own reactor's version | XS | - |
| 68 | [EDI-171](EDI-171.md) | The backlog is reconciled after an analysis of its overlaps | XS | - |
| 69 | [EDI-172](EDI-172.md) | An item's status and issue live only in its own file, so deliveries stop conflicting in the index | S | - |
| 66 | [EDI-168](EDI-168.md) | CI is green again on main | XS | - |

### Wave 2: after the foundations of wave 1 and the lifecycle domain

| Order | Item | Title | Size | Blocked by |
|---|---|---|---|---|
| 5 | [EDI-85](EDI-85.md) | The stage is stored | M | - |
| 6 | [EDI-83](EDI-83.md) | The anchor as an index | S | EDI-81 |

### Wave 3: after the stage is stored

| Order | Item | Title | Size | Blocked by |
|---|---|---|---|---|
| 7 | [EDI-88](EDI-88.md) | Plans and their waits | M | EDI-85 |
| 8 | [EDI-86](EDI-86.md) | Delivered, changing, ready | M | EDI-85 |
| 9 | [EDI-70](EDI-70.md) | Landing by the merged head: squash-merge cleanup | M | EDI-85 |
| 10 | [EDI-87](EDI-87.md) | The reasons a task waits, as a set | M | EDI-85 |
| 57 | [EDI-156](EDI-156.md) | Finished work retires itself once nothing is left to do on it | S | EDI-65 |
| 58 | [EDI-157](EDI-157.md) | A merge request's validation watch never reads as changing | XS | EDI-86 |
| 59 | [EDI-161](EDI-161.md) | A worker's done is checked and booked by the core in one pass | M | EDI-86, EDI-163 |
| 61 | [EDI-163](EDI-163.md) | The tracker walks a ticket to a named state and checks tickets against what landed | S | - |

### Wave 4: plans dispatch themselves

| Order | Item | Title | Size | Blocked by |
|---|---|---|---|---|
| 11 | [EDI-89](EDI-89.md) | The plan dispatches its slices | M | EDI-88 |
| 12 | [EDI-71](EDI-71.md) | One effort word per plan | S | EDI-88 |
| 13 | [EDI-65](EDI-65.md) | The page, the report and the digest on stages: report ready, not ongoing | M | EDI-86, EDI-70 |
| 14 | [EDI-67](EDI-67.md) | The base moved: open requests stay mergeable | S | EDI-86 |

### Wave 5: the console plan and the report, once plans dispatch themselves

| Order | Item | Title | Size | Blocked by |
|---|---|---|---|---|
| 15 | [EDI-90](EDI-90.md) | The captain's last look and the delta | S | EDI-65 |
| 16 | [EDI-91](EDI-91.md) | The report's new layout | M | EDI-90, EDI-133 |
| 17 | [EDI-92](EDI-92.md) | The stop hook's new rule | S | EDI-90 |
| 18 | [EDI-55](EDI-55.md) | Quiet delivers the primary's own notifications at once | S | - |
| 19 | [EDI-56](EDI-56.md) | autoQuietAfter accepts PT0S | XS | - |
| 20 | [EDI-57](EDI-57.md) | Link chips on every row | M | - |
| 21 | [EDI-58](EDI-58.md) | View buttons in the header | S | - |
| 22 | [EDI-59](EDI-59.md) | The task panel | M | EDI-57, EDI-58 |
| 23 | [EDI-60](EDI-60.md) | Live check-in on a worker | M | EDI-59 |
| 24 | [EDI-61](EDI-61.md) | The Fleet view | M | EDI-58, EDI-60 |
| 25 | [EDI-62](EDI-62.md) | The Week view | S | EDI-58 |
| 26 | [EDI-63](EDI-63.md) | Tab title and browser notification | S | - |
| 27 | [EDI-64](EDI-64.md) | Quiet and away buttons | S | - |
| 28 | [EDI-66](EDI-66.md) | edi board show | S | - |
| 29 | [EDI-68](EDI-68.md) | edi park and edi resume | M | EDI-87, EDI-89 |
| 30 | [EDI-69](EDI-69.md) | A standing CI note per project | S | EDI-89 |
| 31 | [EDI-72](EDI-72.md) | Spike: a steer at the next tool call | S | EDI-89 |
| 32 | [EDI-73](EDI-73.md) | EDI's concept art in the repository | S | EDI-89 |
| 33 | [EDI-93](EDI-93.md) | Plans on the page and in the report | S | EDI-89, EDI-91, EDI-59 |
| 34 | [EDI-96](EDI-96.md) | Ship-raised decisions batched into console rounds | M | EDI-59 |

### Wave 6: after the Week view and a week of use

| Order | Item | Title | Size | Blocked by |
|---|---|---|---|---|
| 35 | [EDI-97](EDI-97.md) | Effort and review yield recorded per task, shown in the Week view | M | EDI-62 |
| 36 | [EDI-76](EDI-76.md) | Introspect again after a week of the EDI-52 and EDI-74 changes | M | EDI-93 |

### Security findings

The findings of the read-only security review of 28 October 2026, held on the captain's machine for the cloud session.
The four worker-escape items come first, then the pinned actions the captain named alongside them, then the low findings.
The four worker-escape items and the pinned actions had to land before the repository is made public ([EDI-132](EDI-132.md)); the captain decided on 30 October 2026 that the other findings do not block it, since EDI runs locally for the captain alone.
The section sits after the six waves; the captain may move it up.

| Order | Item | Title | Size | Severity | Blocked by |
|---|---|---|---|---|---|
| 37 | [EDI-122](EDI-122.md) | A worker can never act as the primary by dropping its token file | S | medium | - |
| 38 | [EDI-123](EDI-123.md) | A worker can never type into the captain's primary session or reach the edi tmux socket another way | M | medium | - |
| 39 | [EDI-124](EDI-124.md) | The shell secret guard matches paths, not the literal text of a word | M | medium | - |
| 40 | [EDI-125](EDI-125.md) | A worker cannot switch its own hooks off through local Claude Code settings | S | medium, unverified | - |
| 41 | [EDI-131](EDI-131.md) | CI actions are pinned to commit SHAs | XS | info | - |
| 42 | [EDI-126](EDI-126.md) | The console checks the Host header and refuses to be framed | S | low | - |
| 43 | [EDI-127](EDI-127.md) | The console's sign-in link never appears on a process command line | S | low | - |
| 44 | [EDI-128](EDI-128.md) | Shell snippets built from configuration validate what they interpolate | S | low | - |
| 45 | [EDI-129](EDI-129.md) | The EDI home and its store are readable by the captain only | S | low | - |
| 46 | [EDI-130](EDI-130.md) | The publish-check endpoint only checks paths EDI owns | S | low | - |
| 48 | [EDI-138](EDI-138.md) | The worker-identity and tmux guards close the bypasses found after EDI-122 and EDI-123 landed | M | medium | - |
| 49 | [EDI-139](EDI-139.md) | Tests never reach the captain's real Outlook calendar | S | low | - |
| 50 | [EDI-140](EDI-140.md) | PinnedActionsTest reads the parsed workflow, and the CI service image and mise version are pinned too | XS | info | - |
| 51 | [EDI-150](EDI-150.md) | Pull request and merge request texts pass the publish guard before EDI posts them | M | medium | - |
| 67 | [EDI-170](EDI-170.md) | The publish guard finds a secret wherever it falls in a large diff | M | low | - |

### Held, parked and needs the captain's machine, no wave

| Order | Item | Title | Size | Blocked by |
|---|---|---|---|---|
| - | [EDI-110](EDI-110.md) | The console header hides its stardate and machine slots while the core does not answer | XS | - |
| - | [EDI-111](EDI-111.md) | An adopted task's title drops the old harness's kind prefix | XS | - |
| - | [EDI-112](EDI-112.md) | Show the full session name in the status bar, and give EDI release versions | M | - |
| - | [EDI-113](EDI-113.md) | Guard Grep against the secret files, as Read is | S | - |
| - | [EDI-114](EDI-114.md) | Native builds use a fixed share of the cores, so two can run side by side | M | - |
| - | [EDI-109](EDI-109.md) | Worker builds get NVD_API_KEY from the captain's secrets file at call time | M | - |
| - | [EDI-1](EDI-1.md) | Clear the high-severity vite advisory in the console's lockfile | XS | - |
| - | [EDI-105](EDI-105.md) | Bring the validation pipeline into EDI's own code flow, replacing no-mistakes | L | EDI-89 |
| - | [EDI-120](EDI-120.md) | WaitControllerTest no longer races its own wake, and no test opens a real browser | S | - |
| - | [EDI-133](EDI-133.md) | The turn report shows only what needs the captain; the sitrep keeps the overview | M | - |
| - | [EDI-134](EDI-134.md) | edi merged reports the cleanup once the copy is gone, never a half-removed copy as unlanded work | S | - |
| - | [EDI-132](EDI-132.md) | The EDI repository cleaned of every personal and employer trace and made public | L | EDI-122, EDI-123, EDI-124, EDI-125, EDI-131 |
| - | [EDI-136](EDI-136.md) | Every open backlog item gets its GitHub issue | S | - |
| - | [EDI-137](EDI-137.md) | Workers reach none of the captain's secrets by permission, not by denylist: a separate OS user, or Docker Sandbox's token substitution | L | - |
| - | [EDI-141](EDI-141.md) | Workarounds on a backlog item with no task read as ticketed, and stow-list survives a broken registry | S | - |
| - | [EDI-144](EDI-144.md) | Validations are recorded and run the project's verify commands, in shadow | L | EDI-105, EDI-85 |
| - | [EDI-145](EDI-145.md) | A supervised reviewer judges each validation against the intent | L | EDI-105 |
| - | [EDI-146](EDI-146.md) | The worker fixes findings on top, and a branch behind its base merges it | M | EDI-105, EDI-67 |
| - | [EDI-147](EDI-147.md) | EDI publishes a validated head on the bound account and opens the draft | M | EDI-105, EDI-70 |
| - | [EDI-148](EDI-148.md) | EDI watches a request's checks and marks it ready for review | M | EDI-105, EDI-86 |
| - | [EDI-149](EDI-149.md) | Projects cut over to EDI's validation, and no-mistakes is removed | L | EDI-105 |

## Not in the backlog

- Work on other projects (the integration, container, branding and notification items of registered projects) belongs to those projects, not to this repository.
- Work already delivered is in git history and the decision records, not here.
