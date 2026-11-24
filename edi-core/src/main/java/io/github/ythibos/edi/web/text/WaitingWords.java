package io.github.ythibos.edi.web.text;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.application.fleet.OngoingTask;
import io.github.ythibos.edi.domain.delivery.Delivery;

public final class WaitingWords {

    private static final int FEW_WORDS = 12;
    private static final long MINUTES_IN_AN_HOUR = 60;

    private WaitingWords() {
    }

    public static String board(String task, int questions, String link) {
        return "Board for " + task + ": " + waiting(questions) + " at " + link;
    }

    public static String review(String task, String address, String stackedOn) {
        return "Ready for your review: " + task + " - " + address + targeting(stackedOn);
    }

    public static String comingBack(String address, String why, String stackedOn) {
        return "changing, back to you when it is ready (" + why + ") - " + address + targeting(stackedOn);
    }

    public static String ongoing(String title, OngoingTask ongoing) {
        return offTheBase(ongoing).map(note -> title + ": " + note).orElse(title);
    }

    public static String doing(String activity, OngoingTask ongoing) {
        return offTheBase(ongoing).map(note -> activity + " - " + note).orElse(activity);
    }

    private static Optional<String> offTheBase(OngoingTask ongoing) {
        return ongoing.stackedRequest().map(WaitingWords::noteOn);
    }

    public static String target(String stackedOn) {
        return "targets " + stackedOn + ", not the base: merging it does not land the work";
    }

    private static String targeting(String stackedOn) {
        return stackedOn == null ? "" : " (" + target(stackedOn) + ")";
    }

    private static String noteOn(Delivery stackedRequest) {
        return stackedRequest.isMerged() ? mergedElsewhere(stackedRequest) : target(stackedRequest.stackedOn());
    }

    private static String mergedElsewhere(Delivery stackedRequest) {
        return "its request merged into " + stackedRequest.stackedOn() + ", so the work did not land through its own request;"
                + " once " + stackedRequest.stackedOn() + " lands on the base and its worker is stopped, edi cleanup " + stackedRequest.task() + " retires it";
    }

    public static String inTheRound(String closesAt) {
        return "waits in the console for your round at " + closesAt;
    }

    public static String workerQuestion(String task, String question, Duration waited) {
        return "Waiting on EDI: " + task + " asks \"" + inAFewWords(question) + "\" - waiting " + forHowLong(waited);
    }

    private static String waiting(int questions) {
        return questions > 0 ? Words.quantity(questions, "question") + " waiting" : "waiting";
    }

    private static String inAFewWords(String question) {
        List<String> words = Arrays.asList(question.strip().split("\\s+"));
        return words.size() <= FEW_WORDS ? String.join(" ", words) : String.join(" ", words.subList(0, FEW_WORDS)) + " ...";
    }

    private static String forHowLong(Duration waited) {
        long minutes = Math.max(0, waited.toMinutes());
        if (minutes == 0) {
            return "under a minute";
        }
        if (minutes < MINUTES_IN_AN_HOUR) {
            return Words.quantity((int) minutes, "minute");
        }
        long hours = minutes / MINUTES_IN_AN_HOUR;
        long rest = minutes % MINUTES_IN_AN_HOUR;
        return Words.quantity((int) hours, "hour") + (rest == 0 ? "" : " " + Words.quantity((int) rest, "minute"));
    }
}
