package io.github.ythibos.edi.web.text;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public final class WaitingWords {

    private static final int FEW_WORDS = 12;
    private static final long MINUTES_IN_AN_HOUR = 60;

    private WaitingWords() {
    }

    public static String board(String task, int questions, String link) {
        return "Board for " + task + ": " + waiting(questions) + " at " + link;
    }

    public static String review(String task, String address) {
        return "Ready for your review: " + task + " - " + address;
    }

    public static String changing(String task, String address, String why) {
        return "Changing, hold the merge: " + task + " (" + why + ") - " + address;
    }

    public static String inTheRound(String closesAt) {
        return "in the console now, and in your round of decisions at " + closesAt;
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
