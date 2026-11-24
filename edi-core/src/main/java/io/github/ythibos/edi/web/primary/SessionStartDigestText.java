package io.github.ythibos.edi.web.primary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.application.fleet.ChangingRequest;
import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ParkedTask;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.WaitingBoard;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.application.memory.MemoryCheck;
import io.github.ythibos.edi.application.primary.SessionAnchor;
import io.github.ythibos.edi.application.primary.SessionStartDigest;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.memory.WorkaroundCount;
import io.github.ythibos.edi.domain.primary.PrimaryOutputStyle;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.web.personality.PersonalityText;
import io.github.ythibos.edi.web.presence.PresenceWords;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.WaitingWords;
import io.github.ythibos.edi.web.text.Words;
import io.github.ythibos.edi.web.worker.HealthWords;
import io.github.ythibos.edi.web.worker.WorkerListText;

@Component
public class SessionStartDigestText implements TextPresenter<SessionStartDigest> {

    private final WorkerListText workers;
    private final PersonalityText personalityText;
    private final PresenceWords presenceWords;
    private final HealthWords healthWords;
    private final Moments moments;

    public SessionStartDigestText(WorkerListText workers, PersonalityText personalityText, PresenceWords presenceWords, HealthWords healthWords,
            Moments moments) {
        this.workers = workers;
        this.personalityText = personalityText;
        this.presenceWords = presenceWords;
        this.healthWords = healthWords;
        this.moments = moments;
    }

    @Override
    public Class<SessionStartDigest> presents() {
        return SessionStartDigest.class;
    }

    @Override
    public String text(SessionStartDigest digest) {
        List<String> lines = new ArrayList<>(List.of("EDI session-start digest"));
        lines.addAll(anchorOf(digest.anchor()));
        lines.add(standingOf(digest.standing()));
        lines.addAll(personalityText.lines(digest.personality()));
        lines.add("Presence: " + presenceWords.of(digest.presence()) + ".");
        lines.addAll(sitrepOf(digest.sitrep()));
        lines.addAll(memoryOf(digest.memory(), digest.workarounds()));
        lines.addAll(setupOf(digest.missingFromTheHome()));
        lines.addAll(limitStop(digest.previousLimitStop()));
        lines.addAll(waitingOnTheCaptain(digest.fleet()));
        lines.add(workers.text(digest.workers()).stripTrailing());
        lines.addAll(parked(digest.fleet()));
        lines.add("Queued: " + digest.fleet().queued().size() + ".");
        lines.addAll(outage(digest));
        lines.add("Wakes not yet handled: " + digest.wakesWaiting() + "; the wake stream prints those this session has not seen.");
        lines.addAll(gardeningOf(digest));
        lines.addAll(waitAdvice(digest));
        return String.join("\n", lines) + "\n";
    }

    private static List<String> anchorOf(SessionAnchor anchor) {
        return switch (anchor.held()) {
            case CURRENT -> List.of(confirmationOf(anchor));
            case OLDER -> List.of(olderAnchorOf(anchor), confirmationOf(anchor));
            case UNWRITTEN -> List.of(unwrittenAnchorOf(anchor), confirmationOf(anchor));
            case OUTSIDE_THE_HOME -> List.of(warningOf(anchor), confirmationOf(anchor));
        };
    }

    private static String olderAnchorOf(SessionAnchor anchor) {
        return "This session holds an older anchor than the core's: the " + PrimaryOutputStyle.NAME + " output style at " + anchor.outputStyle()
                + " predates the core's build, and a compaction or a new start loads that older anchor again until edi up refreshes it. "
                + currentAnchorToRead(anchor);
    }

    private static String currentAnchorToRead(SessionAnchor anchor) {
        return anchorFileToRead(anchor)
                .map(current -> "Read " + current + " now and hold to every line of it.")
                .orElse("The core finds no checkout of its anchor to read, so tell the captain and ask for edi up, which writes the current one into the"
                        + " output style.");
    }

    private static Optional<Path> anchorFileToRead(SessionAnchor anchor) {
        return switch (anchor.held()) {
            case CURRENT, OUTSIDE_THE_HOME -> Optional.of(anchor.outputStyle());
            case OLDER, UNWRITTEN -> anchor.currentAnchor();
        };
    }

    private static String unwrittenAnchorOf(SessionAnchor anchor) {
        return "The " + PrimaryOutputStyle.NAME + " output style at " + anchor.outputStyle() + " does not carry the anchor yet, so Claude Code has none"
                + " to load for this session until edi up writes it. " + currentAnchorToRead(anchor);
    }

    private static String warningOf(SessionAnchor anchor) {
        return "The anchor is not in this session: Claude Code loads it, as the " + PrimaryOutputStyle.NAME
                + " output style the primary's settings name, only for a session started in " + anchor.home() + ". Read " + anchor.outputStyle()
                + " now and hold to every line of it, and tell the captain this session runs without its anchor until edi up starts the primary again.";
    }

    private static String confirmationOf(SessionAnchor anchor) {
        return "Confirm that this session's system prompt holds the heading \"" + anchor.heading() + "\"; if it does not, "
                + anchorFileToRead(anchor).map(file -> "read " + file + " at once and tell the captain").orElse("tell the captain at once")
                + " this session runs without its anchor, for a primary never works unaware.";
    }

    private static String standingOf(Standing standing) {
        return switch (standing) {
            case PRIMARY -> "This session is the primary: it supervises the fleet and answers the captain.";
            case STANDBY -> "This session is standby: another session holds the primary role, so this one only reads until the captain types here.";
            case UNCLAIMED -> "No session holds the primary role; the captain's next typed prompt makes this session the primary.";
        };
    }

    private static List<String> sitrepOf(ConsoleOpening opening) {
        if (opening == null) {
            return List.of("This start did not open the console's sitrep page in the captain's browser; edi console opens it signed in.");
        }
        return List.of(opening.opened()
                ? "The console's sitrep page is open in the captain's browser at " + opening.console() + "."
                : "You could not open the console's sitrep page in the captain's browser; it is at " + opening.console()
                        + ", and edi console opens it signed in.");
    }

    private static List<String> memoryOf(MemoryCheck memory, WorkaroundCount workarounds) {
        String measured = memory.estimatedTokens() + " of " + memory.budgetTokens() + " tokens";
        if (memory.passes()) {
            return List.of("Startup memory: " + measured + "; it is part of this session's system prompt, and if it is not, read "
                    + memoryFilesOf(memory) + " now." + workaroundsOf(workarounds));
        }
        return List.of("Startup memory needs curating (" + measured + (memory.duplicatedHeadings().isEmpty() ? "" : ", repeated rule headings")
                + "): run /stow; edi memory stow-list lists what to curate." + workaroundsOf(workarounds));
    }

    private static String memoryFilesOf(MemoryCheck memory) {
        return memory.files().stream().map(file -> file.path().toString()).collect(Collectors.joining(", "));
    }

    private static String workaroundsOf(WorkaroundCount workarounds) {
        if (!workarounds.anyToCurate()) {
            return "";
        }
        return " Workarounds in learnings.md: " + workarounds.expired() + " expired and " + workarounds.unticketed()
                + " unticketed; /stow deletes an expired one and gives an unticketed one a ticket or a date.";
    }

    private static List<String> setupOf(List<String> missing) {
        return missing.isEmpty() ? List.of() : List.of("The EDI home lacks " + String.join(", ", missing)
                + "; examples/edi-home in the EDI repository shows each file's shape, and the captain decides their content.");
    }

    private List<String> limitStop(PrimarySession stopped) {
        if (stopped == null) {
            return List.of();
        }
        return List.of("The previous primary session stopped on " + healthWords.limit(stopped.limit()) + " at "
                + moments.minute(stopped.limitReachedAt()) + "; the workers parked on a limit are listed below.");
    }

    private static List<String> waitingOnTheCaptain(FleetSnapshot fleet) {
        WaitingOnTheCaptain waiting = fleet.waiting();
        List<String> lines = new ArrayList<>(List.of(Words.counted("Waiting on the captain", waiting.count())));
        waiting.decisions().forEach(open -> lines.add("  " + decisionLine(open)));
        waiting.boards().forEach(board -> lines.addAll(boardLines(board)));
        waiting.reviews().forEach(ready -> lines.add("  " + reviewLine(ready)));
        waiting.changing().forEach(changing -> lines.add("  " + changingLine(changing)));
        return lines;
    }

    private static String decisionLine(OpenDecision open) {
        return open.decision().number() + "  " + open.task().id() + "  " + open.decision().card().title();
    }

    private static List<String> boardLines(WaitingBoard waiting) {
        List<String> lines = new ArrayList<>(List.of("  " + boardLine(waiting)));
        waiting.decisions().forEach(open -> lines.add("    " + decisionLine(open)));
        return lines;
    }

    private static String boardLine(WaitingBoard waiting) {
        return "board  " + waiting.task().id() + "  " + questionsAt(waiting.board().openQuestions()) + waiting.board().link();
    }

    private static String questionsAt(int questions) {
        return questions > 0 ? Words.quantity(questions, "question") + " at " : "";
    }

    private static String reviewLine(ReadyForReview ready) {
        return "review  " + ready.task().id() + "  " + ready.delivery().address().url() + targeting(ready.delivery());
    }

    private static String changingLine(ChangingRequest changing) {
        return "changing  " + changing.task().id() + "  " + changing.delivery().address().url() + targeting(changing.delivery()) + "  hold the merge: "
                + changing.whyChanging();
    }

    private static String targeting(Delivery delivery) {
        return delivery.isStacked() ? "  " + WaitingWords.target(delivery.stackedOn()) : "";
    }

    private static List<String> parked(FleetSnapshot fleet) {
        List<String> lines = new ArrayList<>(List.of(Words.counted("Parked", fleet.parked().size())));
        fleet.parked().forEach(parked -> lines.add("  " + parkedLine(parked)));
        return lines;
    }

    private static String parkedLine(ParkedTask parked) {
        return parked.task().id() + "  " + parked.task().holds().held().stream()
                .map(hold -> hold.why() + "  " + Moments.until(hold.until()))
                .collect(Collectors.joining("; "));
    }

    private static List<String> outage(SessionStartDigest digest) {
        if (digest.outage().isEmpty()) {
            return List.of();
        }
        return List.of(Words.counted("Outage hold list", digest.outage().size()) + ": edi recover shows it; relaunch only the numbers the captain names.");
    }

    private static List<String> gardeningOf(SessionStartDigest digest) {
        return digest.gardeningDue() && digest.standing() != Standing.STANDBY
                ? List.of("It is Monday after the weekly usage reset: offer the captain the weekly gardening round, the upkeep check included (gardening).")
                : List.of();
    }

    private static List<String> waitAdvice(SessionStartDigest digest) {
        boolean workInFlight = !digest.fleet().ongoing().isEmpty() || !digest.workers().entries().isEmpty();
        return digest.standing() == Standing.STANDBY || !workInFlight ? List.of() : List.of("Work is in flight: keep one wake stream, edi wait --follow alone with the Monitor tool.");
    }
}
