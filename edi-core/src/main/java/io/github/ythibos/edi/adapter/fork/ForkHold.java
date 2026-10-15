package io.github.ythibos.edi.adapter.fork;

import java.time.LocalDate;

public record ForkHold(String reason, String kind, LocalDate until) {

    private static final String CAPTAIN = "captain";

    public boolean isForTheCaptain() {
        return CAPTAIN.equals(kind);
    }
}
