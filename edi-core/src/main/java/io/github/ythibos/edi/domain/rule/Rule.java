package io.github.ythibos.edi.domain.rule;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Rule {
    SIGNAL_BY_PATTERN(RuleArea.GUARD, "Processes are signalled by their recorded id, never by a name pattern (I-30)."),
    TMUX_WITHOUT_SOCKET(RuleArea.GUARD, "tmux runs only on a named socket, so the captain's own server is never touched (I-31)."),
    EDI_SERVER_CONTROL(RuleArea.GUARD, "The edi tmux server is never killed, typed into or respawned (I-31)."),
    TMUX_INPUT(RuleArea.GUARD, "A worker never types, pastes or runs commands into a tmux pane."),
    FORCED_PUSH(RuleArea.GUARD, "History on a remote is never rewritten by a forced push (I-03)."),
    BASE_BRANCH_PUSH(RuleArea.GUARD, "Nothing is pushed to a base branch, so every change goes through review (I-03)."),
    MERGE(RuleArea.GUARD, "Only the captain merges (I-03)."),
    SKIPPED_HOOKS(RuleArea.GUARD, "git's commit-msg and pre-push checks are never skipped."),
    OUTSIDE_ISOLATED_COPY(RuleArea.GUARD, "A worker writes only inside its isolated copy and its task directory (I-07)."),
    CLAUDE_SETTINGS(RuleArea.GUARD, "A worker never writes its copy's Claude Code settings, which hold the hooks that guard it."),
    SECRET(RuleArea.GUARD, "Secret files and forge tokens are never read or printed (I-32)."),
    WORKER_IDENTITY(RuleArea.GUARD, "A worker never unsets or reassigns the variables that name it to the core."),
    UNLANDED_BRANCH_DELETION(RuleArea.GUARD, "The branch of a task still queued or in flight is never deleted."),
    DIALOG_ANSWERED_AHEAD(RuleArea.GUARD, "A dialog to the captain arrives without answers, since only the captain answers it."),
    HOOK_FROM_THE_SHELL(RuleArea.GUARD, "The hooks are fed only by Claude Code, never from a shell."),
    EDI_WAIT_NOT_ALONE(RuleArea.GUARD, "edi wait runs alone, never piped, redirected or sent to the background."),
    WAKE_STREAM_OUTSIDE_MONITOR(RuleArea.GUARD, "The primary's wake stream runs only under the Monitor tool, where its lines are read."),
    UNREADABLE_COMMAND(RuleArea.GUARD, "A shell command the guard cannot read is refused rather than let through."),
    CORE_UNREACHABLE(RuleArea.GUARD, "A tool call the guard cannot check is refused, except the plain edi calls and dialogs that bring the core back."),
    STOP_WITH_UNREAD_INBOX(RuleArea.HOOK, "A worker does not stop while a message in its inbox is unread."),
    TURN_END_WITH_WORK_PENDING(RuleArea.HOOK, "The primary's turn does not end while a wake or its own work is pending."),
    HOOK_NOT_TAKEN(RuleArea.HOOK, "A hook the core cannot take is reported to Claude Code, never dropped in silence."),
    API_TOKEN(RuleArea.ACCESS, "Only the home token, a live worker's token or a console session reaches the core's API."),
    CONSOLE_HEADER(RuleArea.ACCESS, "The console changes state only with its own header, so no other page submits as the captain."),
    LOOPBACK_HOST(RuleArea.ACCESS, "The core answers only requests addressed to its own loopback host."),
    CALLER_ROLE(RuleArea.ACCESS, "Each command runs only under the role it belongs to: the primary, a worker or the captain."),
    WORKER_TOKEN_FILE(RuleArea.ACCESS, "A worker's window calls the core with its own token, never the home token."),
    REQUEST_VALID(RuleArea.REQUEST, "A request to the core carries every value it needs, well formed."),
    CAPTAINS_WORD(RuleArea.AUTHORITY, "Destructive and irreversible acts wait for the captain's word (I-06)."),
    TYPED_WORDS(RuleArea.AUTHORITY, "Only words the captain typed in a known primary session count as the captain's word."),
    TASK_STAGE(RuleArea.TASK, "A task moves only along the stages its kind allows."),
    TASK_HOLD(RuleArea.TASK, "A held task stays held until its hold is released or its decision answered."),
    TASK_INTAKE(RuleArea.TASK, "A new task names a registered project and an id never used before."),
    PLAN_INTAKE(RuleArea.TASK, "A plan is known by an umbrella key no other plan has, and works on no more slices at once than its project."),
    WORK_KEY(RuleArea.TASK, "A task books its work on one key of its project's tracker."),
    TASK_DISPATCHED(RuleArea.TASK, "A task's worker, brief and copy exist only once it is dispatched."),
    ANSWERED_ONCE(RuleArea.DECISION, "A decision is answered once, and a new question is a new decision."),
    CAPTAIN_VOICE(RuleArea.DECISION, "What reaches the captain speaks in outcomes, not in internal labels (I-22)."),
    TICKET_CHOICE(RuleArea.DECISION, "The captain's ticket choice is one of the options offered."),
    WAITS_MET(RuleArea.DISPATCH, "A task is dispatched only once each task it waits for is merged or landed, as its wait names."),
    ONE_PROJECT(RuleArea.DISPATCH, "A worker works in the isolated copy of exactly one project."),
    CATEGORY(RuleArea.DISPATCH, "A dispatch names a category from the dispatch rules."),
    MECHANICAL_SHAPE(RuleArea.DISPATCH, "A mechanical task states its exact change and its project names the verify command it is checked by."),
    PROTECTED_FILES(RuleArea.DISPATCH, "A mechanical task never edits a protected file."),
    BRANCH_KIND(RuleArea.DISPATCH, "A branch is of a kind its project takes."),
    ISOLATED_COPY(RuleArea.DISPATCH, "A worker is launched only in an isolated copy of its project's clone, on its own branch."),
    AWAY_WORKER_CAP(RuleArea.DISPATCH, "While the captain is away no more workers run than when they left."),
    RESEARCH_FIRST(RuleArea.DISPATCH, "Implementation after research waits for the research's written plan."),
    CRAFT_RULES(RuleArea.DISPATCH, "A project that follows the craft rules gets them in every brief."),
    BRIEF_LENGTH(RuleArea.DISPATCH, "A brief stays within its line budget."),
    CLONE_CURRENT(RuleArea.DISPATCH, "Follow-up work starts from a clone that holds the merge it follows."),
    WORKER_LIVE(RuleArea.WORKER, "A command reaches only a known worker that is not retired."),
    ONE_WORKER_PER_TASK(RuleArea.WORKER, "One worker serves a task at a time."),
    RELAUNCH_STOPPED_FIRST(RuleArea.WORKER, "A worker is relaunched only once its session stopped and nothing else holds its copy."),
    OWN_PROCESS_SIGNALLED(RuleArea.WORKER, "Only a worker's recorded process is signalled."),
    LAUNCH_HOOKS_INTACT(RuleArea.WORKER, "A worker launches only where no local setting can switch off or redirect its hooks."),
    PANE_TYPING(RuleArea.WORKER, "The core types into a worker's pane only at its idle prompt, and stops at anything else."),
    CLAUDE_LOGIN(RuleArea.WORKER, "A worker runs, and resumes, only on a Claude login the machine profile names and that is ready for it."),
    EDI_ON_PATH(RuleArea.WORKER, "A worker's hooks reach the edi command by its absolute path."),
    MESSAGE_WAITS(RuleArea.INBOX, "A worker acknowledges only a message that waits for it, and only once."),
    ADDRESSED_TO_EDI(RuleArea.STATUS, "A worker reports to EDI and never addresses the captain."),
    QUESTION_KEY(RuleArea.STATUS, "A status that waits on an answer names its key, so the answer can settle it."),
    PAUSE_END(RuleArea.STATUS, "A paused status says when the pause ends, so an overrun is noticed."),
    DONE_CLAIMS(RuleArea.DELIVERY, "A done is accepted only once its inbox is read, its copy exists and its claims hold."),
    DRAFT_REVIEW_ONLY(RuleArea.DELIVERY, "edi deliver opens drafts only for ships delivered for draft review."),
    DELIVERED_FROM_THE_COPY(RuleArea.DELIVERY, "A delivery comes from an isolated copy that still exists."),
    NOT_MERGED_YET(RuleArea.DELIVERY, "A merged request is followed, never delivered again."),
    DELIVERY_RECORDED(RuleArea.DELIVERY, "Only a recorded delivery is followed."),
    REQUEST_FOLLOWED(RuleArea.DELIVERY, "A merge is followed only in the registered repository, and only once the forge shows it merged."),
    OUTGOING_CLEAN(RuleArea.PUBLISH, "Nothing outgoing carries a secret, a denied term, a personal path, a network address or a SNAPSHOT version."),
    REQUEST_TEXT_CLEAN(RuleArea.PUBLISH, "A merge request's title and description pass the publish check, as a commit does."),
    CHECKABLE_TARGET(RuleArea.PUBLISH, "The publish check runs only in a registered clone or the caller's own isolated copy."),
    REGISTERED_FORGE(RuleArea.PUBLISH, "Work is published only to the repository its project registers."),
    WORKFLOW_SCOPE(RuleArea.PUBLISH, "A push that changes workflows goes only with the workflow scope the captain granted."),
    PUBLISH_CHECK_REACHABLE(RuleArea.PUBLISH, "A push goes out only once the publish check could run."),
    LANDED_BEFORE_DISCARD(RuleArea.CLEANUP, "Unlanded work is never discarded without the captain's word."),
    SCOUT_REPORT(RuleArea.CLEANUP, "A scout is cleaned up only once its done was accepted and its report exists."),
    NO_WORKER_AT_WORK(RuleArea.CLEANUP, "A copy is retired only once no worker works in it."),
    CHANGE_MATCHES_ITS_BRIEF(RuleArea.MECHANICAL, "A mechanical change is accepted only when its diff matches its brief and its verify command passes."),
    OWN_TASK_BOARD(RuleArea.BOARD, "A worker arms boards for its own task only."),
    BOARD_FILE(RuleArea.BOARD, "A research board is an existing file in its task's directory, armed once."),
    OPEN_CARD(RuleArea.BOARD, "A board round answers a decision that waits on the captain only with that very card."),
    ROUND_SHAPE(RuleArea.BOARD, "A board round names its link and puts a question or a decision before the captain."),
    BOARD_LISTENING(RuleArea.BOARD, "A round is posted only to a board that still listens."),
    BOARD_ANSWER(RuleArea.BOARD, "A board's answer settles a decision only when it answers that decision's card."),
    SPARK_SHAPE(RuleArea.SPARK, "A spark says its idea, about one task or one registered project at most."),
    SPARK_SORTED_ONCE(RuleArea.SPARK, "A spark is sorted once, and a dropped spark says why."),
    TRACKER_CONFIGURED(RuleArea.TRACKER, "A tracker is reached only with the settings and keys this machine names for it."),
    TRACKER_ANSWERED(RuleArea.TRACKER, "A tracker call that is refused or finds nothing is reported, never taken as done."),
    TICKET_UPDATE(RuleArea.TRACKER, "A ticket update the tracker refuses is recorded, and the work goes on without it."),
    ONE_WAKE_STREAM(RuleArea.WAKE, "The primary session has exactly one live wake stream."),
    WAIT_READ(RuleArea.WAKE, "edi wait runs only where its output is read."),
    MEMORY_BUDGET(RuleArea.MEMORY, "The memory loaded at start stays within its token budget, with no heading twice."),
    ANCHOR_BUDGET(RuleArea.MEMORY, "The primary's anchor and its personal addendum stay within 10,000 bytes (I-35)."),
    STOP_WITH_WORKERS(RuleArea.CORE, "The core stays up while workers run, unless the captain's word stops them too."),
    CONFIGURATION_VALID(RuleArea.CORE, "The home's configuration files are readable and complete."),
    FORGE_ACCOUNT(RuleArea.CORE, "A forge is reached only under the account its repository is bound to."),
    FLIGHT_RECORDING(RuleArea.CORE, "A flight recording is written out only by a core that keeps one."),
    OUTAGE_LIST(RuleArea.RECOVERY, "A worker lost in an outage is relaunched or dropped only from the outage list, once its limit has reset."),
    ADOPTABLE(RuleArea.MIGRATION, "Only open work in an old isolated copy of one project, on its own branch, is taken over."),
    COMMIT_WORK_KEY(RuleArea.COMMIT, "Every commit subject starts with the task's work key (I-10)."),
    CO_AUTHOR_TRAILER(RuleArea.COMMIT, "A commit carries no Co-authored-by line (I-09)."),
    MODEL_SIGN_OFF(RuleArea.COMMIT, "A Signed-off-by line names a person, never a model, an agent or a tool (I-09)."),
    GENERATED_WITH_LINE(RuleArea.COMMIT, "A commit carries no \"Generated with\" line (I-09)."),
    COMMAND_ARGUMENTS(RuleArea.COMMAND, "An edi command runs only with options and parameters it can read.");

    private final RuleArea area;
    private final String protects;

    Rule(RuleArea area, String protects) {
        this.area = area;
        this.protects = protects;
    }

    public static Optional<Rule> withId(String id) {
        return Arrays.stream(values()).filter(rule -> rule.id().equals(id)).findFirst();
    }

    public String id() {
        return area.word() + "." + withoutTheArea(word());
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public RuleArea area() {
        return area;
    }

    public String protects() {
        return protects;
    }

    private String withoutTheArea(String word) {
        String areaPrefix = area.word() + "-";
        return word.startsWith(areaPrefix) ? word.substring(areaPrefix.length()) : word;
    }
}
