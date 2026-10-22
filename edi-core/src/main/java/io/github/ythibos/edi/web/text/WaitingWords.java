package io.github.ythibos.edi.web.text;

public final class WaitingWords {

    private WaitingWords() {
    }

    public static String board(String task, int questions, String link) {
        return "Board for " + task + ": " + Words.quantity(questions, "question") + " waiting at " + link;
    }

    public static String review(String task, String address) {
        return "Ready for your review: " + task + " - " + address;
    }
}
