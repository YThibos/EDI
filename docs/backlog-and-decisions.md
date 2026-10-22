# The backlog, decisions and reports

The core keeps the backlog: tasks, the holds the captain puts on them, and the decisions waiting on the captain.
A task is a ship, which changes a project, a scout, which researches or designs, or a captain task, which only the captain can do and which needs no project.

## The backlog

```sh
edi project list                                    # the registry in $EDI_HOME/config/projects.yaml
edi task add --kind ship --project EDI --title "Fit the lamp" --key EDI-14
edi backlog --ready                                 # the queue, or only what nothing holds or blocks
edi task show fit-the-lamp                          # a task id, or the D number of its decision
edi hold fit-the-lamp --kind deferral --reason "after the reports" --until 2026-11-01
edi release fit-the-lamp
```

Every ship and scout gets its work key at intake ([ADR-094](decisions/ADR-094-work-keys-are-settled-at-intake.md)); [Trackers and time](trackers-and-time.md) explains where the key comes from.

## Decisions

```sh
edi decision raise fit-the-lamp --card - < card.json   # prints the decision's D number
edi decision answer D58 --option A --text "D58, A"     # the captain's words, verbatim
edi decision list --open
```

A decision holds its task until the captain answers it ([ADR-039](decisions/ADR-039-a-decision-holds-its-task.md)), and its D number is assigned once and never reused.
A card is `{"title", "context", "options": [{"key", "text", "authorises"}], "recommend": {"key", "why"}}`.
An option's optional `"authorises"` is `"promote"`, `"drop"` or `"discard-work"`: choosing that option is the captain's word for that act on the task, and no other answer is.

## Reports

One fleet snapshot sorts the open work into decisions waiting on the captain, ongoing, parked and queued work ([ADR-046](decisions/ADR-046-where-the-fleet-snapshot-puts-open-work.md)), and `edi report` and `edi sitrep` print it as the boxed captain report ([ADR-047](decisions/ADR-047-the-captain-report-matches-the-old-renderer.md)).

```sh
edi report --turn - < turn.json                     # the boxed captain report that ends a turn
edi sitrep                                          # the same report, read-only and without a turn
```

A turn is `{"done": [{"projects", "text"}], "next": [{"projects", "text"}], "tasks": {"<task>": {"text", "state", "projects"}}}`, every part optional.
Inside tmux the report fits the pane it runs in ([ADR-048](decisions/ADR-048-the-command-measures-its-pane.md)).
Every captain-facing card and report is linted for internal labels ([ADR-128](decisions/ADR-128-the-voice-lint-keeps-internal-labels-from-the-captain.md)).
Every time a report, a decision list or a presence line shows is in the machine's own time zone, such as "Answered 2026-10-22 11:14 CEST", while the store, the event log and every API keep UTC instants ([ADR-155](decisions/ADR-155-edi-shows-the-machines-local-time-and-stores-utc.md)).

## The captain's word

Acts only the captain may authorise, such as promoting a scout, dropping a task, discarding unlanded work or touching a secret, need the captain's own typed words from the current session ([ADR-115](decisions/ADR-115-the-captains-word-is-typed-in-the-current-exchange.md), [ADR-117](decisions/ADR-117-dropping-and-discarding-work-on-the-captains-word.md)).

```sh
edi captain said --task fit-the-lamp --text "Drop it, and discard its work."   # the captain's words, as typed this session
edi captain said --text "Read the token to check it."                          # the captain's word on what concerns no single task
edi task promote survey-the-lamps --title "Fit the lamps"                      # a scout's ship, queued on the captain's word
edi task drop fit-the-lamp --discard-work                                      # on the captain's word; unlanded work only with --discard-work
edi worktree discard fit-the-lamp                                              # the copy and branch, on the captain's word; the task stays
```

A promoted scout queues a new ship ([ADR-116](decisions/ADR-116-a-promoted-scout-queues-a-new-ship.md)).
