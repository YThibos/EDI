package io.github.ythibos.edi.web.console;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.LandedTask;
import io.github.ythibos.edi.application.fleet.OngoingTask;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ParkedTask;
import io.github.ythibos.edi.application.fleet.RecentAnswer;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Answer;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Card;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Charted;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Landed;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Ongoing;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Option;
import io.github.ythibos.edi.web.console.ConsoleSitrep.PresenceShown;
import io.github.ythibos.edi.web.console.ConsoleSitrep.Recommendation;
import io.github.ythibos.edi.web.presence.PresenceWords;

@Component
public class SitrepConsolePresenter {

    static final int LANDED_SHOWN = 10;
    static final int CHARTED_SHOWN = 20;
    private static final String WITHOUT_A_WORKER = "waiting";

    private final PresenceWords presenceWords;

    public SitrepConsolePresenter(PresenceWords presenceWords) {
        this.presenceWords = presenceWords;
    }

    public ConsoleSitrep present(FleetSnapshot fleet, Instant generated, ZoneId zone, String machine, CaptainAddress address, Presence presence) {
        List<Charted> charted = chartedNext(fleet);
        return new ConsoleSitrep(
                ConsoleSitrep.EDI_SITREP_V1,
                generated,
                zone.getId(),
                machine,
                address.word(),
                presenceShown(presence, generated),
                fleet.decisions().stream().map(SitrepConsolePresenter::card).toList(),
                fleet.answered().stream().map(SitrepConsolePresenter::answer).toList(),
                fleet.ongoing().stream().map(SitrepConsolePresenter::ongoing).toList(),
                fleet.landed().stream().limit(LANDED_SHOWN).map(SitrepConsolePresenter::landed).toList(),
                fleet.landed().size(),
                charted.stream().limit(CHARTED_SHOWN).toList(),
                charted.size());
    }

    private PresenceShown presenceShown(Presence presence, Instant generated) {
        return new PresenceShown(presence.mode().word(), presence.demoHoldsAt(generated), presenceWords.of(presence));
    }

    private static List<Charted> chartedNext(FleetSnapshot fleet) {
        Stream<ChartedTask> queued = fleet.queued().stream().map(task -> new ChartedTask(task, queued(task)));
        Stream<ChartedTask> parked = fleet.parked().stream().map(parkedTask -> new ChartedTask(parkedTask.task(), parked(parkedTask)));
        return Stream.concat(queued, parked).sorted(Comparator.comparing(ChartedTask::filed)).map(ChartedTask::row).toList();
    }

    private static Card card(OpenDecision open) {
        DecisionCard card = open.decision().card();
        return new Card(open.decision().number().toString(), open.task().id().value(), projectsOf(open.task()), card.title(), card.context(),
                card.options().stream().map(option -> new Option(option.key().toString(), option.text())).toList(),
                new Recommendation(card.recommendation().key().toString(), card.recommendation().why()), open.decision().raisedAt());
    }

    private static Answer answer(RecentAnswer recent) {
        Decision decision = recent.decision();
        CaptainAnswer answer = decision.answer();
        return new Answer(decision.number().toString(), recent.task().id().value(), projectsOf(recent.task()), decision.card().title(), answer.verbatim(),
                answer.choosesAnOption() ? answer.option().toString() : null, answer.channel().word(), answer.answeredAt());
    }

    private static Ongoing ongoing(OngoingTask ongoing) {
        Task task = ongoing.task();
        return new Ongoing(task.id().value(), task.intake().title(), task.intake().kind().word(), projectsOf(task), stateOf(ongoing.worker()),
                doingOf(ongoing), draftOf(ongoing.delivery()));
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

    private static String stateOf(Worker worker) {
        if (worker == null) {
            return WITHOUT_A_WORKER;
        }
        if (worker.lastStatus() == null) {
            return worker.activity().word();
        }
        StatusState reported = worker.lastStatus().report().state();
        return reported == StatusState.RESOLVED ? StatusState.WORKING.word() : reported.word();
    }

    private static String doingOf(OngoingTask ongoing) {
        PipelineRun run = ongoing.pipelineRun();
        if (run != null && run.isRunning()) {
            return "pipeline " + (run.activeStep() == null ? run.status() : run.activeStep());
        }
        Worker worker = ongoing.worker();
        if (worker == null) {
            return "no worker yet";
        }
        return worker.lastStatus() == null ? worker.activity().word() : worker.lastStatus().report().text();
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
