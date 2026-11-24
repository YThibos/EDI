package io.github.ythibos.edi.web.report;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.fleet.DealtWith;
import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ParkedTask;
import io.github.ythibos.edi.domain.call.CallKey;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.HeavyRuns;
import io.github.ythibos.edi.web.presence.PresenceWords;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.WaitingWords;

@Component
public class CaptainReportPresenter implements TextPresenter<CaptainReport> {

    private static final int BACKLOG_SHOWN = 5;
    private static final Comparator<Task> NEWEST_FILED_FIRST = Comparator.comparing(CaptainReportPresenter::filedOn).reversed();
    private static final Function<OpenDecision, DecisionShowing> EVERY_CARD = _ -> DecisionShowing.IN_FULL;

    private final PresenceWords presenceWords;

    public CaptainReportPresenter(PresenceWords presenceWords) {
        this.presenceWords = presenceWords;
    }

    @Override
    public Class<CaptainReport> presents() {
        return CaptainReport.class;
    }

    @Override
    public String text(CaptainReport report) {
        TurnWording wording = new TurnWording(report.turn());
        FleetSnapshot fleet = report.fleet();
        int width = report.width().columns();
        List<String> lines = new ArrayList<>(presenceOf(report, width));
        lines.addAll(DecisionZone.lines(WaitingEntries.of(fleet.waiting(), wording, EVERY_CARD), width));
        lines.addAll(dealtWithSection(fleet.dealtWith(), wording, width));
        lines.addAll(countedSection("DONE THIS TURN", ReportLines.turnItems(report.turn().done()), "Nothing finished this turn", width));
        lines.addAll(countedSection("ONGOING", ongoingItems(fleet, wording), "Nothing running", width));
        lines.addAll(section("NEXT", ProjectColumn.lines(ReportLines.turnItems(report.turn().next()), "Nothing planned", width)));
        lines.addAll(parkedSection(fleet, wording, width));
        lines.addAll(backlogSection(fleet, wording, width));
        return lines.stream().map(ReportLines::withoutTrailingSpaces).collect(Collectors.joining("\n", "", "\n"));
    }

    private List<String> presenceOf(CaptainReport report, int width) {
        List<String> lines = new ArrayList<>();
        if (report.showsThePresence()) {
            lines.addAll(ReportLines.standing("Presence: " + presenceWords.of(report.presence()), width));
        }
        if (report.showsTheUnreadableCalendar()) {
            lines.addAll(ReportLines.standing("Calendar: " + presenceWords.ofTheUnreadableCalendar(report.unreadableCalendar()), width));
        }
        if (report.showsTheHeavyRunsAtOnce()) {
            lines.addAll(ReportLines.standing("Builds: " + heavyRunsOf(report.heavyRunsAtOnce()), width));
        }
        if (!lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static String heavyRunsOf(HeavyRuns atOnce) {
        List<String> runs = atOnce.runs().stream().map(run -> run.worker().task() + "'s " + run.run().words()).toList();
        return String.join(", ", runs.subList(0, runs.size() - 1)) + " and " + runs.getLast()
                + " run at once; I cap none of them, so each is slower until they finish.";
    }

    private static LocalDate filedOn(Task task) {
        return LocalDate.ofInstant(task.created(), ZoneOffset.UTC);
    }

    private static List<String> countedSection(String title, List<ReportItem> items, String placeholder, int width) {
        return afterABlankLine(ReportLines.countedSection(title, items, placeholder, width));
    }

    private static List<String> dealtWithSection(DealtWith dealtWith, TurnWording wording, int width) {
        if (dealtWith.isEmpty()) {
            return List.of();
        }
        List<ReportItem> items = Stream.of(
                        dealtWith.decisions().stream().map(answered -> answeredItem(answered, wording)),
                        dealtWith.boards().stream().map(ended -> endedBoardItem(ended, wording)),
                        dealtWith.reviews().stream().map(settled -> settledRequestItem(settled, wording)))
                .flatMap(Function.identity())
                .toList();
        return countedSection("DEALT WITH, WAITING FOR YOUR TICK", items, "", width);
    }

    private static ReportItem answeredItem(DealtWith.Answered answered, TurnWording wording) {
        Decision decision = answered.decision();
        return new ReportItem(wording.projectsOf(answered.task()), new CallKey.OfADecision(decision.number()) + " " + decision.card().title() + " - answered");
    }

    private static ReportItem endedBoardItem(DealtWith.EndedBoard ended, TurnWording wording) {
        return new ReportItem(wording.projectsOf(ended.task()), new CallKey.OfABoard(ended.board().id()) + " Board for " + wording.textOf(ended.task())
                + " - ended");
    }

    private static ReportItem settledRequestItem(DealtWith.SettledRequest settled, TurnWording wording) {
        return new ReportItem(wording.projectsOf(settled.task()), new CallKey.OfAReview(settled.task().id()) + " " + wording.textOf(settled.task())
                + " - " + settled.delivery().state().word() + " " + settled.delivery().address().url());
    }

    private static List<ReportItem> ongoingItems(FleetSnapshot fleet, TurnWording wording) {
        return fleet.ongoing().stream()
                .map(ongoing -> new ReportItem(wording.projectsOf(ongoing.task()),
                        WaitingWords.ongoing(wording.textWithStateOf(ongoing.task()), ongoing) + comingBack(fleet, ongoing.task())))
                .toList();
    }

    private static String comingBack(FleetSnapshot fleet, Task task) {
        return fleet.comingBackOf(task.id())
                .map(changing -> " - " + WaitingWords.comingBack(changing.delivery().address().url(), changing.whyChanging(),
                        changing.delivery().stackedOn()))
                .orElse("");
    }

    private static List<String> section(String heading, List<String> body) {
        return afterABlankLine(ReportLines.headed(heading, body));
    }

    private static List<String> parkedSection(FleetSnapshot fleet, TurnWording wording, int width) {
        if (fleet.parked().isEmpty()) {
            return List.of();
        }
        return countedSection("PARKED", fleet.parked().stream().map(parked -> parkedItem(parked, wording)).toList(), "", width);
    }

    private static List<String> backlogSection(FleetSnapshot fleet, TurnWording wording, int width) {
        if (!fleet.isQuiet() || fleet.queued().isEmpty()) {
            return List.of();
        }
        List<Task> newestFirst = fleet.queued().stream().sorted(NEWEST_FILED_FIRST).toList();
        List<ReportItem> shown = newestFirst.stream().limit(BACKLOG_SHOWN).map(task -> queuedItem(task, wording)).toList();
        List<String> body = new ArrayList<>(ProjectColumn.lines(shown, "", width));
        if (newestFirst.size() > shown.size()) {
            body.add(ProjectColumn.unlabelled("(+" + (newestFirst.size() - shown.size()) + " more queued)"));
        }
        return section(ReportLines.counted("BACKLOG", newestFirst.size()), body);
    }

    private static List<String> afterABlankLine(List<String> section) {
        List<String> lines = new ArrayList<>(List.of(""));
        lines.addAll(section);
        return lines;
    }

    private static ReportItem parkedItem(ParkedTask parked, TurnWording wording) {
        return new ReportItem(wording.projectsOf(parked.task()), parkedTitleOf(parked, wording) + " - " + parked.whyParked());
    }

    private static ReportItem queuedItem(Task task, TurnWording wording) {
        return new ReportItem(wording.projectsOf(task), wording.textOf(task));
    }

    private static String parkedTitleOf(ParkedTask parked, TurnWording wording) {
        Decision decision = parked.decision();
        return decision == null ? wording.textOf(parked.task()) : decision.number() + " " + decision.card().title();
    }
}
