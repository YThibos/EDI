package io.github.ythibos.edi.web.report;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.OngoingTask;
import io.github.ythibos.edi.application.fleet.ParkedTask;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.web.presence.PresenceWords;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
public class CaptainReportPresenter implements TextPresenter<CaptainReport> {

    private static final int BACKLOG_SHOWN = 5;
    private static final String SECTION_RULE = "─";
    private static final Comparator<Task> NEWEST_FILED_FIRST = Comparator.comparing(CaptainReportPresenter::filedOn).reversed();

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
        lines.addAll(DecisionZone.lines(decisionsOf(fleet, wording), width));
        lines.addAll(countedSection("DONE THIS TURN", turnItems(report.turn().done()), "Nothing finished this turn", width));
        lines.addAll(countedSection("ONGOING", ongoingItems(fleet, wording), "Nothing running", width));
        lines.addAll(section("NEXT", ProjectColumn.lines(turnItems(report.turn().next()), "Nothing planned", width)));
        lines.addAll(parkedSection(fleet, wording, width));
        lines.addAll(backlogSection(fleet, wording, width));
        return lines.stream().map(CaptainReportPresenter::withoutTrailingSpaces).collect(Collectors.joining("\n", "", "\n"));
    }

    private List<String> presenceOf(CaptainReport report, int width) {
        if (!report.showsThePresence()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(WordWrap.hanging(Glyphs.safe("Presence: " + presenceWords.of(report.presence())), width, "", "  "));
        lines.add("");
        return lines;
    }

    private static LocalDate filedOn(Task task) {
        return LocalDate.ofInstant(task.created(), ZoneOffset.UTC);
    }

    private static List<DecisionZone.Entry> decisionsOf(FleetSnapshot fleet, TurnWording wording) {
        return fleet.decisions().stream()
                .map(open -> new DecisionZone.Entry(open.decision().number(), wording.projectsOf(open.task()), open.decision().card()))
                .toList();
    }

    private static List<String> countedSection(String title, List<ReportItem> items, String placeholder, int width) {
        return section(counted(title, items.size()), ProjectColumn.lines(items, placeholder, width));
    }

    private static List<ReportItem> turnItems(List<TurnItem> items) {
        return items.stream().map(item -> new ReportItem(new ProjectSet(item.projects()), item.text())).toList();
    }

    private static List<ReportItem> ongoingItems(FleetSnapshot fleet, TurnWording wording) {
        return fleet.ongoing().stream()
                .map(OngoingTask::task)
                .map(task -> new ReportItem(wording.projectsOf(task), wording.textWithStateOf(task)))
                .toList();
    }

    private static List<String> section(String heading, List<String> body) {
        List<String> lines = new ArrayList<>(List.of("", heading, SECTION_RULE.repeat(WordWrap.columns(heading))));
        lines.addAll(body);
        return lines;
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
        return section(counted("BACKLOG", newestFirst.size()), body);
    }

    private static String withoutTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') {
            end--;
        }
        return line.substring(0, end);
    }

    private static String counted(String title, int count) {
        return title + "  (" + count + ")";
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
