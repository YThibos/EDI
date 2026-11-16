package io.github.ythibos.edi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.delivery.DeliverDraft.DeliveredDraft;
import io.github.ythibos.edi.application.delivery.DroppedTask;
import io.github.ythibos.edi.application.delivery.FollowMerges.CleanUp;
import io.github.ythibos.edi.application.delivery.FollowMerges.MergedWork;
import io.github.ythibos.edi.application.delivery.RecountedCosts;
import io.github.ythibos.edi.application.delivery.RecountedCosts.Recount;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.UnresumedSession;
import io.github.ythibos.edi.application.task.AddedTask;
import io.github.ythibos.edi.application.tracker.KeyedTask;
import io.github.ythibos.edi.application.worker.SteerDelivery;
import io.github.ythibos.edi.application.worker.SteerWorker;
import io.github.ythibos.edi.application.worker.StopWorker.StoppedWorker;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;
import io.github.ythibos.edi.domain.worker.BootId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.Stages;

class ConfirmationTextTest {

    private static final TaskId LANTERN = new TaskId("lantern");

    private final ConfirmationText text = new ConfirmationText();

    @Test
    void confirmsEachChangeInOneLine() {
        assertThat(text.text(new Confirmation.Queued(new AddedTask(new TaskId("lantern"), null, null)))).isEqualTo("Queued lantern.\n");
        assertThat(text.text(new Confirmation.Noted(new TaskId("lantern")))).isEqualTo("Noted on lantern.\n");
        assertThat(text.text(new Confirmation.Held(PresenterFixtures.heldHorn())))
                .isEqualTo("Held fog-horn (deferral, until 2026-11-01): after the storm\n");
        assertThat(text.text(new Confirmation.Released(PresenterFixtures.lantern()))).isEqualTo("Released lantern; it is queued again.\n");
        assertThat(text.text(new Confirmation.Raised(new DecisionNumber(58)))).isEqualTo("D58\n");
    }

    @Test
    void confirmsAnAnswerAndWhatBecameOfItsTask() {
        Task closed = Stages.answered(PresenterFixtures.captainItem()
                .heldForDecision(PresenterFixtures.openDecision().holdOnItsTask()), Instant.parse("2026-10-15T10:00:00Z"));

        assertThat(text.text(new Confirmation.Answered(PresenterFixtures.answeredDecision(), closed, TicketSettlement.none(), Optional.empty())))
                .isEqualTo("Recorded the answer to D58 (option A); lamp-choice is done.\n");
    }

    @Test
    void confirmsAnAnswerCarriedToTheWorkerWaitingOnItsQuestion() {
        Task released = PresenterFixtures.lantern();
        Worker waiting = relaunchedAs(LaunchKind.DISPATCH);
        InboxMessage carried = InboxMessage.sent(LANTERN, 2, "Answer to D58", new StatusKey("lamp-choice"), PresenterFixtures.AT);
        Optional<SteerWorker.Steered> steered = Optional.of(new SteerWorker.Steered(carried, waiting, SteerDelivery.AT_THE_NEXT_STOP));

        assertThat(text.text(new Confirmation.Answered(PresenterFixtures.answeredDecision(), released, TicketSettlement.none(), steered)))
                .isEqualTo("Recorded the answer to D58 (option A); lantern is queued again. The answer resolves lamp-choice, the question its worker waits on."
                        + " Message 2 waits for lantern#2; it is busy, so its next stop delivers the message.\n");
    }

    @Test
    void confirmsAnAnswerCarriedToItsWorkerWithoutResolvingTheQuestionItAskedLater() {
        Task released = PresenterFixtures.lantern();
        Worker waiting = relaunchedAs(LaunchKind.DISPATCH);
        InboxMessage carried = InboxMessage.sent(LANTERN, 2, "Answer to D58", null, PresenterFixtures.AT);
        Optional<SteerWorker.Steered> steered = Optional.of(new SteerWorker.Steered(carried, waiting, SteerDelivery.AT_THE_NEXT_STOP));

        assertThat(text.text(new Confirmation.Answered(PresenterFixtures.answeredDecision(), released, TicketSettlement.none(), steered)))
                .isEqualTo("Recorded the answer to D58 (option A); lantern is queued again. The answer goes to its worker without resolving the question"
                        + " it asked after the decision was raised; that question stays open. Message 2 waits for lantern#2; it is busy, so its next stop delivers the message.\n");
    }

    @Test
    void confirmsTheTicketATaskBooksItsWorkOn() {
        WorkKey lamps = WorkKey.parse("SVC-40");

        assertThat(text.text(new Confirmation.Queued(new AddedTask(LANTERN, new Ticket(lamps, "Harbour lamps", "To Do"), null))))
                .isEqualTo("Queued lantern on SVC-40: Harbour lamps.\n");
        assertThat(text.text(new Confirmation.Queued(new AddedTask(LANTERN, null, new DecisionNumber(59)))))
                .isEqualTo("Queued lantern; D59 asks which ticket it books on, and it waits for that answer.\n");
        assertThat(answerWith(new TicketSettlement.Keyed(lamps, true))).endsWith(" It books its work on SVC-40, the ticket you created.\n");
        assertThat(answerWith(new TicketSettlement.Keyed(lamps, false))).endsWith(" It books its work on SVC-40.\n");
        assertThat(answerWith(new TicketSettlement.CreatedWithoutItsFields(lamps, "the epic link is not allowed")))
                .endsWith(" It books its work on SVC-40, the ticket you created, but the tracker refused its fields: the epic link is not allowed\n");
        assertThat(answerWith(new TicketSettlement.Pending("JIRA cannot be reached.")))
                .endsWith(" Its ticket is not settled yet: JIRA cannot be reached. You try again every 30 seconds.\n");
        assertThat(answerWith(new TicketSettlement.AskedAgain("JIRA found nothing for the ticket SVC-77 (HTTP 404).")))
                .endsWith(" You could not carry out the answer, so you raised the decision again: JIRA found nothing for the ticket SVC-77 (HTTP 404).\n");
        assertThat(answerWith(new TicketSettlement.DropChosen())).endsWith(" It books on no ticket; the answer authorises edi task drop lamp-choice.\n");
    }

    @Test
    void confirmsAKeyGivenAfterIntakeAndTheTicketDecisionItOrADropAnswers() {
        Task lantern = PresenterFixtures.lantern();

        assertThat(text.text(new Confirmation.Keyed(new KeyedTask(lantern, null)))).isEqualTo("Keyed lantern on HRN-3.\n");
        assertThat(text.text(new Confirmation.Keyed(new KeyedTask(lantern, new DecisionNumber(87)))))
                .isEqualTo("Keyed lantern on HRN-3; the key answers D87, its ticket decision, and releases it.\n");
        assertThat(text.text(new Confirmation.Dropped(new DroppedTask(lantern, List.of(), new DecisionNumber(87)))))
                .isEqualTo("Dropped lantern on the captain's word; the drop answers D87, its ticket decision; nothing unlanded was discarded.\n");
    }

    @Test
    void confirmsARecountOfTaskCostsWithEachCostItChanged() {
        LocalDate since = LocalDate.parse("2026-11-02");
        TaskCost inflated = new TaskCost(new TaskId("lantern"), new TokenUsage(1, 2, 3, 4), new TokenUsage(905040, 900000, 5000, 700), Instant.parse("2026-11-02T08:00:00Z"));
        TaskCost unknown = new TaskCost(new TaskId("lamp"), new TokenUsage(1, 2, 3, 4), null, Instant.parse("2026-11-02T08:00:00Z"));
        TokenUsage spent = new TokenUsage(40, 900000, 5000, 700);
        List<Recount> changed = List.of(new Recount(inflated, inflated.withPipelineSpend(spent)), new Recount(unknown, unknown.withPipelineSpend(spent)));

        assertThat(text.text(new Confirmation.CostsRecounted(new RecountedCosts(since, 3, changed)))).isEqualTo(
                "Recounted the pipeline's spend of 3 task costs counted since 2026-11-02; 2 changed: lantern from 1810740 to 905740 tokens;"
                        + " lamp from unknown to 905740 tokens.\n");
        assertThat(text.text(new Confirmation.CostsRecounted(new RecountedCosts(since, 1, List.of()))))
                .isEqualTo("Recounted the pipeline's spend of 1 task cost counted since 2026-11-02; none changed.\n");
    }

    @Test
    void confirmsAStopAndASteerWithWhatAFullContextNeeds() {
        Worker lamp = Worker.launched(WorkerId.first(LANTERN), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), new WorkerWindow("edi", 1L, PresenterFixtures.AT, "@1", 2L),
                new WorkerLaunch(LaunchKind.DISPATCH, PresenterFixtures.AT, new BootId("boot"), LoginName.DEFAULT));
        Instant stopped = PresenterFixtures.AT.plusSeconds(60);

        assertThat(text.text(new Confirmation.StopRequested(new StoppedWorker(lamp.stopRequested(stopped), List.of()))))
                .isEqualTo("Stopping lantern#1: you signalled its processes, and those still running in ten seconds are killed;"
                        + " edi worker relaunch lantern resumes its session.\n");
        assertThat(text.text(new Confirmation.StopRequested(new StoppedWorker(lamp.stopRequested(stopped), List.of(4101L)))))
                .startsWith("Stopping lantern#1: you signalled its processes and process 4101, which it left running in its copy, and those still");
        assertThat(text.text(new Confirmation.StopRequested(new StoppedWorker(lamp.stopRequested(stopped), List.of(4101L, 4102L)))))
                .startsWith("Stopping lantern#1: you signalled its processes and processes 4101, 4102, which it left running in its copy, and");
        assertThat(text.text(new Confirmation.StopRequested(new StoppedWorker(lamp.promptSubmitted(stopped).contextFilled(stopped).stopRequested(stopped),
                List.of()))))
                .endsWith("; edi worker relaunch lantern starts a fresh session with a handover, since its context is full.\n");
        assertThat(text.text(new Confirmation.Steered(new SteerWorker.Steered(InboxMessage.sent(LANTERN, 1, "Use the brass lamp.", null, stopped), lamp,
                SteerDelivery.WAITING_FOR_A_FRESH_SESSION)))).isEqualTo("Message 1 waits for lantern#1, whose context is full; the message waits in its"
                        + " inbox for the fresh session that edi worker relaunch starts.\n");
    }

    @Test
    void confirmsWhetherARelaunchResumedItsSessionOrStartedAfresh() {
        UUID lost = UUID.fromString("4d532fb2-0000-4000-8000-000000000001");
        Worker resumed = relaunchedAs(LaunchKind.RESUME);
        Worker fresh = relaunchedAs(LaunchKind.FRESH);
        BranchName branch = BranchName.parse("feature/HRN-3");
        Path copy = Path.of("/edi/worktrees/harness/lantern");

        assertThat(text.text(new Confirmation.Relaunched(new Launched(resumed, branch, copy, null)))).isEqualTo("Relaunched lantern as lantern#2"
                + " (implementation, claude-opus-5-5 at high), resuming session " + resumed.session() + ", in /edi/worktrees/harness/lantern.\n");
        assertThat(text.text(new Confirmation.Relaunched(new Launched(fresh, branch, copy, null))))
                .contains(", with a fresh session and a handover, in /edi/worktrees/harness/lantern.\n");
        assertThat(text.text(new Confirmation.Relaunched(new Launched(fresh, branch, copy, UnresumedSession.withoutATranscript(lost))))).contains(", with a fresh session and a handover"
                + " because session 4d532fb2-0000-4000-8000-000000000001 left no transcript to resume, in /edi/worktrees/harness/lantern.\n");
    }

    @Test
    void confirmsADeliveryAMergeAndACleanUp() {
        MergeRequestAddress address = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 7);
        MergeRequestView merged = new MergeRequestView(address, MergeRequestState.MERGED, true, "feature/HRN-3", "main", "1111", "3333", Instant.parse("2026-10-16T10:00:00Z"));
        Delivery delivery = Delivery.of(LANTERN, new MergeRequestView(address, MergeRequestState.OPEN, true, "feature/HRN-3", "main", "1111", null, null),
                Instant.parse("2026-10-16T09:00:00Z"));

        assertThat(text.text(new Confirmation.DraftDelivered(new DeliveredDraft(delivery, true)))).isEqualTo(
                "Opened the draft https://github.com/example-owner/harness/pull/7 for lantern; you watch it until the captain merges it.\n");
        assertThat(text.text(new Confirmation.DraftDelivered(new DeliveredDraft(delivery, false)))).startsWith("Recorded the open draft ");
        assertThat(text.text(new Confirmation.MergedWorkFollowedUp(new MergedWork(LANTERN, delivery.observed(merged, Instant.parse("2026-10-16T10:00:00Z")), new CleanUp(true, null, false))))).isEqualTo(
                "Recorded the merge of https://github.com/example-owner/harness/pull/7 as 3333; lantern is cleaned up and done.\n");
        assertThat(text.text(new Confirmation.MergedWorkFollowedUp(new MergedWork(LANTERN, null, new CleanUp(false, "a commit is not landed", false)))))
                .isEqualTo("The captain merged lantern locally; lantern is not cleaned up yet: a commit is not landed\n");
        assertThat(text.text(new Confirmation.CleanedUp(PresenterFixtures.lantern()))).isEqualTo(
                "Cleaned up lantern: its work is landed, its isolated copy and branch are removed, and the task is done.\n");
    }

    private String answerWith(TicketSettlement ticket) {
        Task released = Stages.answered(PresenterFixtures.captainItem()
                .heldForDecision(PresenterFixtures.openDecision().holdOnItsTask()), Instant.parse("2026-10-15T10:00:00Z"));
        return text.text(new Confirmation.Answered(PresenterFixtures.answeredDecision(), released, ticket, Optional.empty()));
    }

    private static Worker relaunchedAs(LaunchKind kind) {
        return Worker.launched(WorkerId.first(LANTERN).next(), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), new WorkerWindow("edi", 1L, PresenterFixtures.AT, "@2", 3L),
                new WorkerLaunch(kind, PresenterFixtures.AT, new BootId("boot"), LoginName.DEFAULT));
    }
}
