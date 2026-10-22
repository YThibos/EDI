package io.github.ythibos.edi.web.primary;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ParkedTask;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.WaitingBoard;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.application.memory.MemoryCheck;
import io.github.ythibos.edi.application.primary.SessionStartDigest;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.web.presence.PresenceWords;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;
import io.github.ythibos.edi.web.worker.HealthWords;
import io.github.ythibos.edi.web.worker.WorkerListText;

@Component
public class SessionStartDigestText implements TextPresenter<SessionStartDigest> {

    private final WorkerListText workers;

    public SessionStartDigestText(WorkerListText workers) {
        this.workers = workers;
    }

    @Override
    public Class<SessionStartDigest> presents() {
        return SessionStartDigest.class;
    }

    @Override
    public String text(SessionStartDigest digest) {
        List<String> lines = new ArrayList<>(List.of("EDI session-start digest", standingOf(digest.standing()),
                "Address the captain as \"" + digest.address().word() + "\"."));
        lines.add("Presence: " + PresenceWords.of(digest.presence()) + ".");
        lines.addAll(sitrepOf(digest.sitrep()));
        lines.addAll(memoryOf(digest.memory()));
        lines.addAll(setupOf(digest.missingFromTheHome()));
        lines.addAll(limitStop(digest.previousLimitStop()));
        lines.addAll(waitingOnTheCaptain(digest.fleet()));
        lines.add(workers.text(digest.workers()).stripTrailing());
        lines.addAll(parked(digest.fleet()));
        lines.add("Queued: " + digest.fleet().queued().size() + ".");
        lines.addAll(outage(digest));
        lines.add("Wakes waiting: " + digest.wakesWaiting() + "; edi wait prints them.");
        lines.addAll(upkeepOf(digest));
        lines.addAll(waitAdvice(digest));
        return String.join("\n", lines) + "\n";
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
            return List.of();
        }
        return List.of(opening.opened()
                ? "The console's sitrep page is open in the captain's browser at " + opening.console() + "."
                : "EDI could not open the console's sitrep page in the captain's browser; it is at " + opening.console()
                        + ", and edi console opens it signed in.");
    }

    private static List<String> memoryOf(MemoryCheck memory) {
        String measured = memory.estimatedTokens() + " of " + memory.budgetTokens() + " tokens";
        if (memory.passes()) {
            return List.of("Startup memory: " + measured + "; it is part of this session's system prompt, and if it is not, read "
                    + memoryFilesOf(memory) + " now.");
        }
        return List.of("Startup memory needs curating (" + measured + (memory.duplicatedHeadings().isEmpty() ? "" : ", repeated rule headings")
                + "): run /stow; edi memory stow-list lists what to curate.");
    }

    private static String memoryFilesOf(MemoryCheck memory) {
        return memory.files().stream().map(file -> file.path().toString()).collect(Collectors.joining(", "));
    }

    private static List<String> setupOf(List<String> missing) {
        return missing.isEmpty() ? List.of() : List.of("The EDI home lacks " + String.join(", ", missing)
                + "; examples/edi-home in the EDI repository shows each file's shape, and the captain decides their content.");
    }

    private static List<String> limitStop(PrimarySession stopped) {
        if (stopped == null) {
            return List.of();
        }
        return List.of("The previous primary session stopped on " + HealthWords.limit(stopped.limit()) + " at "
                + Moments.minute(stopped.limitReachedAt()) + "; the workers parked on a limit are listed below.");
    }

    private static List<String> waitingOnTheCaptain(FleetSnapshot fleet) {
        WaitingOnTheCaptain waiting = fleet.waiting();
        List<String> lines = new ArrayList<>(List.of(Words.counted("Waiting on the captain", waiting.count())));
        waiting.decisions().forEach(open -> lines.add("  " + decisionLine(open)));
        waiting.boards().forEach(board -> lines.add("  " + boardLine(board)));
        waiting.reviews().forEach(ready -> lines.add("  " + reviewLine(ready)));
        return lines;
    }

    private static String decisionLine(OpenDecision open) {
        return open.decision().number() + "  " + open.task().id() + "  " + open.decision().card().title();
    }

    private static String boardLine(WaitingBoard waiting) {
        return "board  " + waiting.task().id() + "  " + Words.quantity(waiting.board().openQuestions(), "question") + " at " + waiting.board().link();
    }

    private static String reviewLine(ReadyForReview ready) {
        return "review  " + ready.task().id() + "  " + ready.delivery().address().url();
    }

    private static List<String> parked(FleetSnapshot fleet) {
        List<String> lines = new ArrayList<>(List.of(Words.counted("Parked", fleet.parked().size())));
        fleet.parked().forEach(parked -> lines.add("  " + parkedLine(parked)));
        return lines;
    }

    private static String parkedLine(ParkedTask parked) {
        return parked.task().id() + "  " + parked.task().hold().reason() + "  " + Moments.until(parked.task().hold().until());
    }

    private static List<String> outage(SessionStartDigest digest) {
        if (digest.outage().isEmpty()) {
            return List.of();
        }
        return List.of(Words.counted("Outage hold list", digest.outage().size()) + ": edi recover shows it; relaunch only the numbers the captain names.");
    }

    private static List<String> upkeepOf(SessionStartDigest digest) {
        return digest.upkeepSuggested() && digest.standing() != Standing.STANDBY
                ? List.of("It is the start of the week: offer the captain the weekly upkeep check, whether EDI and its tools are up to date (edi-upkeep).")
                : List.of();
    }

    private static List<String> waitAdvice(SessionStartDigest digest) {
        boolean workInFlight = !digest.fleet().ongoing().isEmpty() || !digest.workers().entries().isEmpty();
        return digest.standing() == Standing.STANDBY || !workInFlight ? List.of() : List.of("Work is in flight: keep one edi wait running in the background.");
    }
}
