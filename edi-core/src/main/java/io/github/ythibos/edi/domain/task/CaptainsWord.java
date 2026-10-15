package io.github.ythibos.edi.domain.task;

public record CaptainsWord(String verbatim) {

    public CaptainsWord {
        if (verbatim.isBlank()) {
            throw new IllegalArgumentException("The captain's word is the captain's own words.");
        }
    }
}
