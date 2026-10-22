package io.github.ythibos.edi.web.presence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.fleet.LandedTask;
import io.github.ythibos.edi.application.fleet.OngoingTask;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.WaitingBoard;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.application.presence.ReturnBrief;
import io.github.ythibos.edi.application.presence.Setback;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.WaitingWords;
import io.github.ythibos.edi.web.text.Words;

@Component
public class ReturnBriefText implements TextPresenter<ReturnBrief> {

    static final String INSTRUCTION = "EDI return brief: show the captain the lines below verbatim, in one fenced block, before you answer their message.";
    private static final int MOST_SHOWN = 10;

    private final PresenceWords presenceWords;

    public ReturnBriefText(PresenceWords presenceWords) {
        this.presenceWords = presenceWords;
    }

    @Override
    public Class<ReturnBrief> presents() {
        return ReturnBrief.class;
    }

    @Override
    public String text(ReturnBrief brief) {
        List<String> lines = new ArrayList<>(List.of(INSTRUCTION, "While you were away (" + presenceWords.of(brief.left()) + "):"));
        lines.addAll(section("Needs your decision", waitingLines(brief.waiting()), Function.identity()));
        lines.addAll(section("Landed", brief.landed(), landed -> landed.task().intake().title()));
        lines.addAll(section("Failed", brief.setbacks(), ReturnBriefText::setbackLine));
        lines.addAll(section("Running", brief.running(), ReturnBriefText::runningLine));
        return String.join("\n", lines) + "\n";
    }

    private static <T> List<String> section(String heading, List<T> items, Function<T, String> line) {
        List<String> lines = new ArrayList<>(List.of(Words.counted(heading, items.size())));
        items.stream().limit(MOST_SHOWN).map(line).forEach(item -> lines.add("  " + item));
        if (items.size() > MOST_SHOWN) {
            lines.add("  (+" + (items.size() - MOST_SHOWN) + " more; edi sitrep shows them)");
        }
        return lines;
    }

    private static List<String> waitingLines(WaitingOnTheCaptain waiting) {
        return Stream.of(
                        waiting.decisions().stream().map(ReturnBriefText::decisionLine),
                        waiting.boards().stream().map(ReturnBriefText::boardLine),
                        waiting.reviews().stream().map(ReturnBriefText::reviewLine))
                .flatMap(Function.identity())
                .toList();
    }

    private static String decisionLine(OpenDecision open) {
        return open.decision().number() + "  " + open.decision().card().title();
    }

    private static String boardLine(WaitingBoard waiting) {
        return WaitingWords.board(waiting.task().intake().title(), waiting.board().openQuestions(), waiting.board().link());
    }

    private static String reviewLine(ReadyForReview ready) {
        return WaitingWords.review(ready.task().intake().title(), ready.delivery().address().url());
    }

    private static String setbackLine(Setback setback) {
        return setback.title() + ": " + switch (setback.event()) {
            case "StatusReported" -> "failed: " + setback.detail();
            case "WorkerDied" -> "its worker stopped unexpectedly";
            case "LostInOutage" -> "its worker was lost in an outage and waits on the recovery list";
            case "WorkerLaunchFailed", "DeferredLaunchFailed" -> "its worker did not start";
            case "BranchDeliveryFailed", "ChangeNotApplied" -> "its delivery to your local copy failed: " + setback.detail();
            case "CleanupRefused" -> "its cleanup found work that has not landed: " + setback.detail();
            case "RecoveryFailed" -> "its relaunch failed: " + setback.detail();
            default -> "the tracker refused an update: " + setback.detail();
        };
    }

    private static String runningLine(OngoingTask ongoing) {
        return ongoing.task().intake().title();
    }
}
