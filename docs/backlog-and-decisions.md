# The backlog, decisions and reports

The core keeps the backlog: tasks, the holds the captain puts on them, and the decisions waiting on the captain.
A task is a ship, which changes a project, a scout, which researches or designs, or a captain task, which only the captain can do and which needs no project.

## The backlog

```sh
edi project list                                    # the registry in $EDI_HOME/config/projects.yaml
edi task add --kind ship --project EDI --title "Fit the lamp" --key EDI-14   # EDI keys its work by its issue numbers
edi backlog --ready                                 # the queue, or only what nothing holds or blocks
edi task show fit-the-lamp                          # a task id, or the D number of its decision
edi hold fit-the-lamp --kind deferral --reason "after the reports" --until 2026-11-01
edi release fit-the-lamp
```

EDI's own queued and held work is also one GitHub issue per item on its repository, each carrying its full spec, with a pickup script and protocol for a session that has only the repository ([docs/pickup.md](pickup.md), [backlog-in-the-repository](decisions/backlog-in-the-repository.md)).

Every ship and scout gets its work key at intake ([trackers-and-time-booking](decisions/trackers-and-time-booking.md)); [Trackers and time](trackers-and-time.md) explains where the key comes from.

## Sparks

An idea that is not thought through yet is caught as a spark, not put on the backlog, and the weekly round sorts it with the captain ([sparks](decisions/sparks.md)).
A message the captain types in the primary that opens with `Spark:` or `spark:` is caught the same way, as the captain's spark.

```sh
edi spark "A tide clock on the harbour wall"     # the time, the author, the session and the task acted on last
edi spark "Paint it blue" --project EDI          # or name the task or project it is about
edi spark list                                   # the open sparks, oldest first; --json for the whole record
edi spark backlog 3 EDI-190                      # it became that backlog item
edi spark fold 4 EDI-120                         # an existing item already covers it
edi spark drop 5 --reason "One lighthouse is enough."
```

## Decisions

```sh
edi decision raise fit-the-lamp --card - < card.json   # prints the decision's D number
edi decision answer D58 --option A --text "D58, A"     # the captain's words, verbatim
edi decision list --open
```

A decision holds its task until the captain answers it ([captain-decisions](decisions/captain-decisions.md)), and its D number is assigned once and never reused.
Raising a card again on a task updates its open decision under the same number; once the captain answered, the new card is a new decision with a new number, and the answered one stays as he left it ([captain-decisions](decisions/captain-decisions.md)).
A card is `{"title", "context", "options": [{"key", "text", "authorises"}], "recommend": {"key", "why"}, "urgent"}`.
An option's optional `"authorises"` is `"promote"`, `"drop"` or `"discard-work"`: choosing that option is the captain's word for that act on the task, and no other answer is.
A running ship's decision waits in the console and reaches the captain in a round with the others raised within five minutes of the round's first; the optional `"urgent": true` is for a decision that blocks the captain's current work, which reaches him at once ([decision-rounds](decisions/decision-rounds.md)).

## Reports

One fleet snapshot sorts the open work into what waits on the captain, ongoing, parked and queued work, and `edi sitrep` prints it as the boxed captain report ([captain-report](decisions/captain-report.md)).
`edi report` prints the turn report that ends a turn: only what needs the captain, with a decision card in full in the turn it is raised and one line after that, and a running ship's decision that is not urgent always on one line ([turn-report](decisions/turn-report.md)).
What waits on the captain is every open decision, whatever holds or blocks its task, every research board with questions or bound decisions he has not answered, and every request ready for his review; the report, the sitrep page and the return brief all show them under "Needs your decision" ([fleet-snapshot](decisions/fleet-snapshot.md)).
A task that is dropped or finished no longer blocks any other task ([task-intake](decisions/task-intake.md)).

```sh
edi report --turn - < turn.json                     # the turn report: only what needs the captain
edi sitrep                                          # the boxed overview, read-only and without a turn
edi sitrep --turn - < turn.json                     # the boxed overview with a state-changing turn's items
```

A turn is `{"done": [{"projects", "text"}], "next": [{"projects", "text"}], "tasks": {"<task>": {"text", "state", "projects"}}}`, every part optional.
Inside tmux the report fits the pane it runs in ([captain-report](decisions/captain-report.md)).
Every captain-facing card and report is linted for internal labels ([captain-facing-voice](decisions/captain-facing-voice.md)).
Every time a report, a decision list or a presence line shows is in the machine's own time zone, such as "Answered 2026-10-22 11:14 CEST", while the store, the event log and every API keep UTC instants ([time-zones](decisions/time-zones.md)).

## The captain's word

Acts only the captain may authorise, such as promoting a scout, dropping a task, discarding unlanded work or touching a secret, need the captain's own words from the current session ([captains-word](decisions/captains-word.md)).
The primary's hooks store every message the captain types and every dialog answer, numbered, so a word is cited by its number and never retyped.
A dialog option the captain chooses is their word by itself for the one act it opens with and the one task it names, such as `drop fit-the-lamp`.

```sh
edi captain messages                                                           # this session's stored messages, numbered
edi captain said --task fit-the-lamp --message 4                               # the captain's message 4, on a task
edi captain said --message 6 --line 2                                          # one line of a message, on what concerns no single task
edi decision answer D58 --option A --message 7                                 # a decision answered with a stored message or dialog answer
edi task promote survey-the-lamps --title "Fit the lamps"                      # a scout's ship, queued on the captain's word
edi task drop fit-the-lamp --discard-work                                      # on the captain's word; unlanded work only with --discard-work
edi task key fit-the-lamp SVC-40 --message 8                                   # a key after intake, answering an open ticket decision with message 8
edi worktree discard fit-the-lamp                                              # the copy and branch, on the captain's word; the task stays
```

A promoted scout queues a new ship ([captains-word](decisions/captains-word.md)).
