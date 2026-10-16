package io.github.ythibos.edi.domain.decision;

import java.time.Instant;

public record CaptainAnswer(String verbatim, OptionKey option, String interpretation, Instant answeredAt, AnswerChannel channel) {

    public CaptainAnswer {
        interpretation = interpretation.strip();
        if (verbatim.isBlank()) {
            throw new IllegalArgumentException("An answer records the captain's own words.");
        }
    }

    public boolean choosesAnOption() {
        return option != null;
    }
}
