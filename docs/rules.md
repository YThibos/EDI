# Rule catalogue

Every rule EDI enforces has one stable id, and every refusal, warning or check that lets something through after it fires records that id ([rule-firings](decisions/rule-firings.md)).
`edi rules` counts the firings of a period, names the last one of each rule and lists the rules that never fired, and the weekly gardening reads the same recorded firings for its week ([weekly-gardening](decisions/weekly-gardening.md)).
This table is the catalogue: each rule's id, the classes or scripts that fire it, and what it protects.
`RuleCatalogueTest` keeps it true: a rule in the code that is missing here, a row for a rule the code no longer has, a place that no longer fires the rule, or a sentence that differs from the code's fails the build.
`CommandRuleFiringsTest` and `CommitMsgHookFiringsTest` do the same for the command and the `commit-msg` hook, by running them and comparing what fires with the rules this table names for them.
A refusal cannot be written without its rule, because `Refusal` takes one, and a command or a git hook names its rule by the catalogue's id.

| Rule | Where it lives | What it protects |
|---|---|---|
| `guard.signal-by-pattern` | `ProcessSignals` | Processes are signalled by their recorded id, never by a name pattern (I-30). |
| `guard.tmux-without-socket` | `TmuxSockets` | tmux runs only on a named socket, so the captain's own server is never touched (I-31). |
| `guard.edi-server-control` | `TmuxSockets` | The edi tmux server is never killed, typed into or respawned (I-31). |
| `guard.tmux-input` | `TmuxSockets` | A worker never types, pastes or runs commands into a tmux pane. |
| `guard.forced-push` | `GitPushes` | History on a remote is never rewritten by a forced push (I-03). |
| `guard.base-branch-push` | `GitPushes` | Nothing is pushed to a base branch, so every change goes through review (I-03). |
| `guard.merge` | `MergingCommands` | Only the captain merges (I-03). |
| `guard.skipped-hooks` | `SkippedHooks` | git's commit-msg and pre-push checks are never skipped. |
| `guard.outside-isolated-copy` | `GuardToolCall` | A worker writes only inside its isolated copy and its task directory (I-07). |
| `guard.claude-settings` | `SettingsWrites` | A worker never writes its copy's Claude Code settings, which hold the hooks that guard it. |
| `guard.secret` | `GuardToolCall`, `SecretReads` | Secret files and forge tokens are never read or printed (I-32). |
| `guard.worker-identity` | `WorkerIdentity` | A worker never unsets or reassigns the variables that name it to the core. |
| `guard.unlanded-branch-deletion` | `BranchDeletions`, `GuardToolCall` | The branch of a task still queued or in flight is never deleted. |
| `guard.dialog-answered-ahead` | `GuardToolCall` | A dialog to the captain arrives without answers, since only the captain answers it. |
| `guard.hook-from-the-shell` | `HookFeeds` | The hooks are fed only by Claude Code, never from a shell. |
| `guard.edi-wait-not-alone` | `LoneWaits` | edi wait runs alone, never piped, redirected or sent to the background. |
| `guard.wake-stream-outside-monitor` | `LoneWaits` | The primary's wake stream runs only under the Monitor tool, where its lines are read. |
| `guard.unreadable-command` | `ShellGuard` | A shell command the guard cannot read is refused rather than let through. |
| `guard.core-unreachable` | `HookCommand` | A tool call the guard cannot check is refused, except the plain edi calls and dialogs that bring the core back. |
| `hook.stop-with-unread-inbox` | `WorkerHooks` | A worker does not stop while a message in its inbox is unread. |
| `hook.turn-end-with-work-pending` | `PrimaryHooks` | The primary's turn does not end while a wake or its own work is pending. |
| `hook.not-taken` | `HookCommand` | A hook the core cannot take is reported to Claude Code, never dropped in silence. |
| `access.api-token` | `ApiTokenFilter` | Only the home token, a live worker's token or a console session reaches the core's API. |
| `access.console-header` | `ApiTokenFilter` | The console changes state only with its own header, so no other page submits as the captain. |
| `access.loopback-host` | `LoopbackHostFilter` | The core answers only requests addressed to its own loopback host. |
| `access.caller-role` | `NotPermitted` | Each command runs only under the role it belongs to: the primary, a worker or the captain. |
| `access.worker-token-file` | `Edi` | A worker's window calls the core with its own token, never the home token. |
| `request.valid` | `ApiRefusals` | A request to the core carries every value it needs, well formed. |
| `authority.captains-word` | `CaptainsWordEvidence` | Destructive and irreversible acts wait for the captain's word (I-06). |
| `authority.typed-words` | `CaptainMessages`, `CaptainsWordEvidence` | Only words the captain typed in a known primary session count as the captain's word. |
| `task.stage` | `AddTask`, `DiscardWork`, `PromoteScout`, `StageTransitions`, `Task` | A task moves only along the stages its kind allows. |
| `task.hold` | `DropTask`, `Holds`, `RecoverFromOutage`, `RelaunchWorker`, `Task` | A held task stays held until its hold is released or its decision answered. |
| `task.intake` | `AddTask`, `ProjectRegistry` | A new task names a registered project and an id never used before. |
| `task.work-key` | `AdoptForkTask`, `DispatchTask`, `GitHubIssuesTracker`, `NoTracker`, `Task`, `TaskKeys`, `TicketProposals`, `TrackedProject` | A task books its work on one key of its project's tracker. |
| `task.dispatched` | `LaunchWorker`, `WorkerContext`, `WorkerDetailQuery`, `WorkerLookup`, `WrittenBriefQuery` | A task's worker, brief and copy exist only once it is dispatched. |
| `decision.answered-once` | `ArmBoard`, `Decision` | A decision is answered once, and a new question is a new decision. |
| `decision.captain-voice` | `CaptainVoice` | What reaches the captain speaks in outcomes, not in internal labels (I-22). |
| `decision.ticket-choice` | `TicketProposal`, `TicketProposals` | The captain's ticket choice is one of the options offered. |
| `dispatch.blockers-done` | `DispatchTask` | A task is dispatched only once the tasks it waits for are done. |
| `dispatch.one-project` | `AdoptForkTask`, `DispatchTask` | A worker works in the isolated copy of exactly one project. |
| `dispatch.category` | `DispatchTask` | A dispatch names a category from the dispatch rules. |
| `dispatch.mechanical-shape` | `Dispatch`, `DispatchTask`, `ExpectedChange` | A mechanical task states its exact change and its project names the verify command it is checked by. |
| `dispatch.protected-files` | `ExpectedChange` | A mechanical task never edits a protected file. |
| `dispatch.branch-kind` | `DispatchTask` | A branch is of a kind its project takes. |
| `dispatch.isolated-copy` | `IsolatedCopies` | A worker is launched only in an isolated copy of its project's clone, on its own branch. |
| `dispatch.away-worker-cap` | `AwayLimit` | While the captain is away no more workers run than when they left. |
| `dispatch.research-first` | `ResearchBeforeImplementation` | Implementation after research waits for the research's written plan. |
| `dispatch.craft-rules` | `GatherBriefFacts` | A project that follows the craft rules gets them in every brief. |
| `dispatch.brief-length` | `BriefLength` | A brief stays within its line budget. |
| `dispatch.clone-current` | `CloneRefreshes` | Follow-up work starts from a clone that holds the merge it follows. |
| `worker.live` | `Worker`, `WorkerLookup` | A command reaches only a known worker that is not retired. |
| `worker.one-worker-per-task` | `WorkerPlaces` | One worker serves a task at a time. |
| `worker.relaunch-stopped-first` | `LaunchWorker`, `RelaunchWorker` | A worker is relaunched only once its session stopped and nothing else holds its copy. |
| `worker.own-process-signalled` | `StopWorker` | Only a worker's recorded process is signalled. |
| `worker.launch-hooks-intact` | `LocalHooks` | A worker launches only where no local setting can switch off or redirect its hooks. |
| `worker.pane-typing` | `TmuxClaudeRuntime` | The core types into a worker's pane only at its idle prompt, and stops at anything else. |
| `worker.claude-login` | `ClaudeLoginReadiness`, `ClaudeLogins`, `ClaudeWorkspaceTrust`, `LaunchWorker` | A worker runs, and resumes, only on a Claude login the machine profile names and that is ready for it. |
| `worker.edi-on-path` | `TmuxClaudeRuntime` | A worker's hooks reach the edi command by its absolute path. |
| `inbox.message-waits` | `InboxMessage`, `WorkerInbox` | A worker acknowledges only a message that waits for it, and only once. |
| `status.addressed-to-edi` | `StatusReport` | A worker reports to EDI and never addresses the captain. |
| `status.question-key` | `StatusReport` | A status that waits on an answer names its key, so the answer can settle it. |
| `status.pause-end` | `StatusReport` | A paused status says when the pause ends, so an overrun is noticed. |
| `delivery.done-claims` | `DoneClaims` | A done is accepted only once its inbox is read, its copy exists and its claims hold. |
| `delivery.draft-review-only` | `DeliverDraft` | edi deliver opens drafts only for ships delivered for draft review. |
| `delivery.delivered-from-the-copy` | `DeliverDraft` | A delivery comes from an isolated copy that still exists. |
| `delivery.not-merged-yet` | `DeliverDraft` | A merged request is followed, never delivered again. |
| `delivery.recorded` | `DeliverDraft`, `DeliveryRecords`, `FollowMerges` | Only a recorded delivery is followed. |
| `delivery.request-followed` | `FollowMerges` | A merge is followed only in the registered repository, and only once the forge shows it merged. |
| `publish.outgoing-clean` | `DeliverDraft`, `PublishCheckController` | Nothing outgoing carries a secret, a denied term, a personal path, a network address or a SNAPSHOT version. |
| `publish.request-text-clean` | `DeliverDraft` | A merge request's title and description pass the publish check, as a commit does. |
| `publish.checkable-target` | `PublicationTargets` | The publish check runs only in a registered clone or the caller's own isolated copy. |
| `publish.registered-forge` | `RegisteredForge` | Work is published only to the repository its project registers. |
| `publish.workflow-scope` | `PublicationTargets` | A push that changes workflows goes only with the workflow scope the captain granted. |
| `publish.check-reachable` | `PublishCheckCommand` | A push goes out only once the publish check could run. |
| `cleanup.landed-before-discard` | `CleanUpTask`, `DropTask` | Unlanded work is never discarded without the captain's word. |
| `cleanup.scout-report` | `CleanUpTask` | A scout is cleaned up only once its done was accepted and its report exists. |
| `cleanup.no-worker-at-work` | `CopyRetirement` | A copy is retired only once no worker works in it. |
| `mechanical.change-matches-its-brief` | `CheckMechanicalChange` | A mechanical change is accepted only when its diff matches its brief and its verify command passes. |
| `board.own-task-board` | `ArmBoard` | A worker arms boards for its own task only. |
| `board.file` | `ArmBoard`, `BoardRoundsQuery`, `PostBoardRound` | A research board is an existing file in its task's directory, armed once. |
| `board.open-card` | `ArmBoard` | A board round answers a decision that waits on the captain only with that very card. |
| `board.round-shape` | `BoardRound` | A board round names its link and puts a question or a decision before the captain. |
| `board.listening` | `Board` | A round is posted only to a board that still listens. |
| `board.answer` | `SettleBoardAnswers` | A board's answer settles a decision only when it answers that decision's card. |
| `spark.shape` | `CatchSpark`, `Spark` | A spark says its idea, about one task or one registered project at most. |
| `spark.sorted-once` | `Spark`, `SparkSorting` | A spark is sorted once, and a dropped spark says why. |
| `tracker.configured` | `JiraTracker`, `KeysFile`, `TrackedProject`, `TrackerSettings`, `Trackers` | A tracker is reached only with the settings and keys this machine names for it. |
| `tracker.answered` | `JiraCalls`, `JiraTracker`, `NothingFound` | A tracker call that is refused or finds nothing is reported, never taken as done. |
| `tracker.ticket-update` | `FollowTickets`, `TicketProposals` | A ticket update the tracker refuses is recorded, and the work goes on without it. |
| `wake.one-wake-stream` | `StreamPoll`, `WaitController`, `WaitForWakes`, `WakeStreams` | The primary session has exactly one live wake stream. |
| `wake.wait-read` | `WaitCommand` | edi wait runs only where its output is read. |
| `memory.budget` | `DailyMemoryCheck`, `MemoryController` | The memory loaded at start stays within its token budget, with no heading twice. |
| `memory.anchor-budget` | `PrimaryPrompt` | The primary's anchor and its personal addendum stay within 10,000 bytes (I-35). |
| `core.stop-with-workers` | `StopTheCore` | The core stays up while workers run, unless the captain's word stops them too. |
| `core.configuration-valid` | `InvalidConfiguration`, `PipelineConfiguration`, `ProjectEntry` | The home's configuration files are readable and complete. |
| `core.forge-account` | `GitLabForge` | A forge is reached only under the account its repository is bound to. |
| `core.flight-recording` | `FlightRecording` | A flight recording is written out only by a core that keeps one. |
| `recovery.outage-list` | `DropFromOutage`, `RecoverFromOutage` | A worker lost in an outage is relaunched or dropped only from the outage list, once its limit has reset. |
| `migration.adoptable` | `AdoptForkTask`, `ForkMigrationPlanner`, `MigrateFirstofficer` | Only open work in an old isolated copy of one project, on its own branch, is taken over. |
| `commit.work-key` | `commit-msg` | Every commit subject starts with the task's work key (I-10). |
| `commit.co-author-trailer` | `commit-msg` | A commit carries no Co-authored-by line (I-09). |
| `commit.model-sign-off` | `commit-msg` | A Signed-off-by line names a person, never a model, an agent or a tool (I-09). |
| `commit.generated-with-line` | `commit-msg` | A commit carries no "Generated with" line (I-09). |
| `command.arguments` | `Edi` | An edi command runs only with options and parameters it can read. |
