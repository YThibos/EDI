package io.github.ythibos.edi.domain.task;

public enum Leftover {
    COPY("its isolated copy"),
    BRANCH("its branch"),
    WORKER("a worker"),
    BOARD("a board");

    private final String phrase;

    Leftover(String phrase) {
        this.phrase = phrase;
    }

    public String phrase() {
        return phrase;
    }
}
