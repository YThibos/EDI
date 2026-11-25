package io.github.ythibos.edi.web.report;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.fleet.ChangingRequest;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.domain.report.Turn;
import io.github.ythibos.edi.web.presence.PresenceWords;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
public class TurnReportPresenter implements TextPresenter<TurnReport> {

    static final String NOTHING_FOR_THE_CAPTAIN = "Nothing needs you; /sitrep shows the rest.\n";

    private final PresenceWords presenceWords;
    private final Moments moments;

    public TurnReportPresenter(PresenceWords presenceWords, Moments moments) {
        this.presenceWords = presenceWords;
        this.moments = moments;
    }

    @Override
    public Class<TurnReport> presents() {
        return TurnReport.class;
    }

    @Override
    public String text(TurnReport report) {
        if (report.hasNothingForTheCaptain()) {
            return NOTHING_FOR_THE_CAPTAIN;
        }
        int width = report.width().columns();
        return paragraphs(Stream.of(calendarOf(report, width), decisionZoneOf(report, width), doneOf(report.turn(), width), nextOf(report.turn(), width)));
    }

    private List<String> calendarOf(TurnReport report, int width) {
        if (!report.showsTheUnreadableCalendar()) {
            return List.of();
        }
        return ReportLines.standing("Calendar: " + presenceWords.ofTheUnreadableCalendar(report.unreadableCalendar()), width);
    }

    private List<String> decisionZoneOf(TurnReport report, int width) {
        if (report.waiting().hasNothingWaiting()) {
            return List.of();
        }
        TurnWording wording = new TurnWording(report.turn());
        List<DecisionZone.Entry> entries = Stream.concat(WaitingEntries.of(report.waiting(), wording, open -> showingOf(open, report)).stream(),
                report.waiting().changingAgain().stream().map(changing -> changingAgain(changing, wording))).toList();
        return DecisionZone.lines(entries, width);
    }

    private DecisionShowing showingOf(OpenDecision open, TurnReport report) {
        if (report.holdsForTheGatheringRound(open.decision())) {
            return new DecisionShowing.InTheRound(moments.clockTime(report.gatheringRound().closes()));
        }
        return report.showsTheCardOf(open) ? DecisionShowing.IN_FULL : DecisionShowing.AS_A_REMINDER;
    }

    private static DecisionZone.Entry changingAgain(ChangingRequest changing, TurnWording wording) {
        return new DecisionZone.ChangingAgain(wording.projectsOf(changing.task()), wording.textOf(changing.task()), changing.delivery().address().url(),
                changing.whyChanging(), changing.delivery().stackedOn());
    }

    private static List<String> doneOf(Turn turn, int width) {
        return turn.done().isEmpty() ? List.of() : ReportLines.countedSection("DONE THIS TURN", ReportLines.turnItems(turn.done()), "", width);
    }

    private static List<String> nextOf(Turn turn, int width) {
        return turn.next().isEmpty() ? List.of() : ReportLines.headed("NEXT", ProjectColumn.lines(ReportLines.turnItems(turn.next()), "", width));
    }

    private static String paragraphs(Stream<List<String>> blocks) {
        return blocks.filter(not(List::isEmpty))
                .map(block -> block.stream().map(ReportLines::withoutTrailingSpaces).collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n", "", "\n"));
    }
}
