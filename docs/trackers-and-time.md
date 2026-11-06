# Trackers and time

## Work keys

Every ship and scout gets its work key at intake: the next EDI-native key of its project, a ticket the captain chooses or has created through a decision on a JIRA project, or the key it was given once its tracker confirms it ([work-keys-and-branches](decisions/work-keys-and-branches.md), [trackers-and-time-booking](decisions/trackers-and-time-booking.md)).
The key names the task's branch and starts every commit subject.

```sh
edi task add --kind ship --project hobby-tool --title "Fit the lamp"           # the next EDI-native key, such as HOBBY-4
edi task add --kind ship --project example-service --title "Fit the lamp" \
    --issue-type Story --epic EXAMPLE-9 --labels lighting --priority Major     # a decision: an open ticket or the drafted one
edi decision answer D59 --option B --text "D59 B"                              # the captain's answer creates and fills the ticket
```

## Trackers

A project's registry entry names its tracker, `tracker: { kind: jira|edi|github|none, projectKey: KEY }`, and the tracker port has an adapter for each kind ([trackers-and-time-booking](decisions/trackers-and-time-booking.md)).
A `github` project keys its work by its GitHub issue numbers: EDI never allocates a key there, and a ship or scout is added with `--key <KEY>-<the issue's number>` once its issue is open ([work-keys-and-branches](decisions/work-keys-and-branches.md)).
The machine profile's `tracker` section says whether this machine reaches a JIRA instance (`kind: jira`, with its `baseUrl`, the keys file section holding the access token, the evergreen tickets, the workflow's statuses in their forward order, the `mergedStatus`, the `issueType` and the `epicLinkField`) or none (`kind: none`).
EDI reads the access token at the moment of each call and never shows it ([trackers-and-time-booking](decisions/trackers-and-time-booking.md)).
The evergreen tickets are named by their role, such as `coordination` and `tooling`; the console's Week view shows time on a project that is not on the machine's JIRA against the `tooling` ticket ([trackers-and-time-booking](decisions/trackers-and-time-booking.md)).
A JIRA ticket joins the active sprint when work starts, links the draft, and moves to the after-merge status once the merge is followed ([trackers-and-time-booking](decisions/trackers-and-time-booking.md)).
An evergreen ticket only gets worklogs: EDI never places it in a sprint, links a draft to it or moves it after a merge, and EDI knows a ticket as evergreen only while the machine profile lists it.
[examples/edi-home/config/machine.yaml](../examples/edi-home/config/machine.yaml) shows a profile with JIRA; a machine without JIRA or time booking sets `tracker.kind: none` and leaves `time` out.

## Time

EDI writes the activity ledger the time-booking tool books from, `$EDI_HOME/state/fleet-ledger.jsonl`, when the machine profile's `time.fleetLedger` is on ([trackers-and-time-booking](decisions/trackers-and-time-booking.md)).
EDI measures each task's active time from its timeline: the stretches a worker spent working, without the time it waited on a decision, a hold or the captain.
`edi task show` prints it as "Worked: active 2h19m of 3h11m" against the wall clock since the first dispatch, and cleanup books it in the ledger as `task.worked` with the work key and `active_minutes` ([trackers-and-time-booking](decisions/trackers-and-time-booking.md)).
It asks workers for the time they worked, as a `worked:` line in their report, while `time.requireWorkedLine` says so ([worker-brief](decisions/worker-brief.md)).

## Research first

EDI can plan unplanned work in a research task before an implementation worker builds it, measuring that pilot against unplanned ships until the captain decides ([research-first](decisions/research-first.md)).
With `"researchFirst": true` on `implementation-unplanned` or `bugfix` in `config/dispatch-rules.json`, a ship's first dispatch adds and launches a research scout that writes the plan, and the ship waits for it.
Dispatching the ship again once the scout is done runs it as `implementation` with the plan in its brief.

```sh
edi dispatch fix-horn --category bugfix --cause-unknown                         # research first, when the dispatch rules switch it on
```
