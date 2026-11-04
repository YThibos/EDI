package io.github.ythibos.edi.web.console;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.fleet.ChangingRequest;
import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.LandedTask;
import io.github.ythibos.edi.application.fleet.OngoingTask;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ParkedTask;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.RecentAnswer;
import io.github.ythibos.edi.application.fleet.TaskLinks;
import io.github.ythibos.edi.application.fleet.WaitingBoard;
import io.github.ythibos.edi.application.fleet.WorkerQuestion;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Answer;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Board;
import io.github.ythibos.edi.web.console.ConsoleSitrep.BoardDecision;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Card;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Changing;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Charted;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Landed;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Ongoing;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Option;
import io.github.ythibos.edi.web.console.ConsoleSitrep.PresenceShown;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Recommendation;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Review;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Question;
import io.github.ythibos.edi.web.presence.PresenceWords;

@Component
public class SitrepConsolePresenter {

    static final int LANDED_SHOWN = 10;
    static final int CHARTED_SHOWN = 20;
    private static final String WITHOUT_A_WORKER = "waiting";
    private static final String WITH_ME = "with me";
    private static final String AFTER_A_LEADING_KEY = "\\b[\\s:|-]*";

    private final PresenceWords presenceWords;

    public SitrepConsolePresenter(PresenceWords presenceWords) {
        this.presenceWords = presenceWords;
    }

    public ConsoleSitrep present(FleetSnapshot fleet, Instant generated, ZoneId zone, String machine, CaptainAddress address, Presence presence) {
        return present(fleet, generated, zone, machine, address, presence, _ -> Map.of());
    }

    public ConsoleSitrep present(FleetSnapshot fleet, Instant generated, ZoneId zone, String machine, CaptainAddress address, Presence presence,
            Function<Collection<Task>, Map<TaskId, TaskLinks>> linksOf) {
        List<ChartedTask> chartedTasks = chartedTasksOf(fleet);
        List<Charted> charted = chartedTasks.stream().map(ChartedTask::row).toList();
        return new ConsoleSitrep(
                ConsoleSitrep.EDI_SITREP_V1,
                generated,
                zone.getId(),
                machine,
                address.word(),
                presenceShown(presence, generated),
                fleet.waiting().decisions().stream().map(SitrepConsolePresenter::card).toList(),
                fleet.waiting().boards().stream().map(SitrepConsolePresenter::board).toList(),
                fleet.waiting().reviews().stream().map(SitrepConsolePresenter::review).toList(),
                fleet.waiting().changing().stream().map(SitrepConsolePresenter::changing).toList(),
                fleet.waiting().workerQuestions().stream().map(SitrepConsolePresenter::workerQuestion).toList(),
                fleet.answered().stream().map(SitrepConsolePresenter::answer).toList(),
                fleet.ongoing().stream().map(SitrepConsolePresenter::ongoing).toList(),
                fleet.landed().stream().limit(LANDED_SHOWN).map(SitrepConsolePresenter::landed).toList(),
                fleet.landed().size(),
                charted.stream().limit(CHARTED_SHOWN).toList(),
                charted.size(),
                linksShown(linksOf.apply(tasksShown(fleet, chartedTasks))));
    }

    private static List<Task> tasksShown(FleetSnapshot fleet, List<ChartedTask> charted) {
        return Stream.of(
                        fleet.waiting().decisions().stream().map(OpenDecision::task),
                        fleet.waiting().boards().stream().map(WaitingBoard::task),
                        fleet.waiting().reviews().stream().map(ReadyForReview::task),
                        fleet.waiting().changing().stream().map(ChangingRequest::task),
                        fleet.ongoing().stream().map(OngoingTask::task),
                        fleet.landed().stream().limit(LANDED_SHOWN).map(LandedTask::task),
                        charted.stream().limit(CHARTED_SHOWN).map(ChartedTask::task))
                .flatMap(Function.identity())
                .toList();
    }

    private static Map<String, TaskLinks> linksShown(Map<TaskId, TaskLinks> links) {
        Map<String, TaskLinks> shown = new LinkedHashMap<>();
        links.forEach((task, taskLinks) -> shown.put(task.value(), taskLinks));
        return shown;
    }

    private PresenceShown presenceShown(Presence presence, Instant generated) {
        return new PresenceShown(presence.mode().word(), presence.demoHoldsAt(generated), presenceWords.of(presence));
    }

    private static List<ChartedTask> chartedTasksOf(FleetSnapshot fleet) {
        Stream<ChartedTask> queued = fleet.queued().stream().map(task -> new ChartedTask(task, queued(task)));
        Stream<ChartedTask> parked = fleet.parked().stream().map(parkedTask -> new ChartedTask(parkedTask.task(), parked(parkedTask)));
        return Stream.concat(queued, parked).sorted(Comparator.comparing(ChartedTask::filed)).toList();
    }

    private static Card card(OpenDecision open) {
        DecisionCard card = open.decision().card();
        return new Card(open.decision().number().toString(), open.task().id().value(), projectsOf(open.task()), card.title(), card.context(),
                card.options().stream().map(option -> new Option(option.key().toString(), option.text())).toList(),
                new Recommendation(card.recommendation().key().toString(), card.recommendation().why()), open.decision().raisedAt());
    }

    private static Board board(WaitingBoard waiting) {
        Task task = waiting.task();
        return new Board(task.id().value(), task.intake().title(), projectsOf(task), waiting.board().openQuestions(), waiting.board().link(),
                waiting.decisions().stream().map(SitrepConsolePresenter::boardDecision).toList());
    }

    private static BoardDecision boardDecision(OpenDecision open) {
        return new BoardDecision(open.decision().number().toString(), open.decision().card().title());
    }

    private static Review review(ReadyForReview ready) {
        Task task = ready.task();
        return new Review(task.id().value(), task.intake().title(), projectsOf(task), ready.delivery().address().url());
    }

    private static Changing changing(ChangingRequest changing) {
        Task task = changing.task();
        return new Changing(task.id().value(), task.intake().title(), projectsOf(task), changing.delivery().address().url(), changing.whyChanging());
    }

    private static Question workerQuestion(WorkerQuestion waiting) {
        Task task = waiting.task();
        return new Question(task.id().value(), task.intake().title(), projectsOf(task), waiting.question(), waiting.worker().lastStatus().at());
    }

    private static Answer answer(RecentAnswer recent) {
        Decision decision = recent.decision();
        CaptainAnswer answer = decision.answer();
        return new Answer(decision.number().toString(), recent.task().id().value(), projectsOf(recent.task()), decision.card().title(), answer.verbatim(),
                answer.choosesAnOption() ? answer.option().toString() : null, answer.channel().word(), answer.answeredAt());
    }

    private static Ongoing ongoing(OngoingTask ongoing) {
        Task task = ongoing.task();
        return new Ongoing(task.id().value(), keyOf(task.intake()), titleAfterItsKey(task.intake()), task.intake().kind().word(), projectsOf(task),
                stateOf(ongoing.worker()), doingOf(ongoing), draftOf(ongoing.delivery()));
    }

    private static Landed landed(LandedTask landed) {
        Task task = landed.task();
        return new Landed(task.id().value(), task.intake().title(), task.intake().kind().word(), projectsOf(task), task.closed(),
                draftOf(landed.delivery()));
    }

    private static Charted queued(Task task) {
        return new Charted(task.id().value(), task.intake().title(), task.intake().kind().word(), projectsOf(task), null);
    }

    private static Charted parked(ParkedTask parked) {
        Task task = parked.task();
        String title = parked.decision() == null ? task.intake().title() : parked.decision().number() + " " + parked.decision().card().title();
        return new Charted(task.id().value(), title, task.intake().kind().word(), projectsOf(task), parked.whyParked());
    }

    private static List<String> projectsOf(Task task) {
        return task.intake().projects().stream().map(ProjectName::value).toList();
    }

    private static String keyOf(Intake intake) {
        return intake.workKey() == null ? null : intake.workKey().toString();
    }

    private static String titleAfterItsKey(Intake intake) {
        if (intake.workKey() == null) {
            return intake.title();
        }
        Matcher leadingKey = leadingKey(intake.workKey()).matcher(intake.title());
        return leadingKey.lookingAt() ? intake.title().substring(leadingKey.end()) : intake.title();
    }

    private static Pattern leadingKey(WorkKey key) {
        return Pattern.compile(Pattern.quote(key.toString()) + AFTER_A_LEADING_KEY, Pattern.CASE_INSENSITIVE);
    }

    private static String stateOf(Worker worker) {
        if (worker == null) {
            return WITHOUT_A_WORKER;
        }
        if (worker.lastStatus() == null) {
            return worker.activity().word();
        }
        if (worker.waitsOnThePrimary()) {
            return WITH_ME;
        }
        StatusState reported = worker.lastStatus().report().state();
        return reported == StatusState.RESOLVED ? StatusState.WORKING.word() : reported.word();
    }

    private static String doingOf(OngoingTask ongoing) {
        PipelineRun run = ongoing.pipelineRun();
        if (run != null && run.isRunning()) {
            return "pipeline " + run.currentStep();
        }
        Worker worker = ongoing.worker();
        if (worker == null) {
            return "no worker yet";
        }
        return worker.lastStatus() == null ? worker.activity().word() : reportedDoing(worker.lastStatus().report());
    }

    private static String reportedDoing(StatusReport report) {
        if (report.isForThePrimary()) {
            return whatEdiIsSettling(report);
        }
        return report.state() == StatusState.RESOLVED ? "back at work; I settled its question" : report.text();
    }

    private static String whatEdiIsSettling(StatusReport report) {
        return report.state() == StatusState.BLOCKED ? "I am helping it past a block" : "I am settling a question";
    }

    private static String draftOf(Delivery delivery) {
        return delivery == null ? null : delivery.address().url();
    }

    private record ChartedTask(Task task, Charted row) {

        Instant filed() {
            return task.created();
        }
    }
}
