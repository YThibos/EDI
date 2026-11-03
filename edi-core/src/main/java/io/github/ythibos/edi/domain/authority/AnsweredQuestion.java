package io.github.ythibos.edi.domain.authority;

import java.util.Objects;

public record AnsweredQuestion(String question, String chosen, String notes) {

    public AnsweredQuestion {
        if (question == null || question.isBlank() || chosen == null || chosen.isBlank()) {
            throw new IllegalArgumentException("A dialog answer holds the question asked and the option the captain chose.");
        }
        notes = Objects.requireNonNullElse(notes, "").strip();
    }

    public boolean carriesNotes() {
        return !notes.isEmpty();
    }
}
