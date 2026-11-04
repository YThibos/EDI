package io.github.ythibos.edi.domain.tracker;

import java.util.Objects;

import io.github.ythibos.edi.domain.project.WorkKey;

public sealed interface TicketChoice {

    record BookOn(WorkKey key) implements TicketChoice {

        public BookOn {
            Objects.requireNonNull(key, "The captain chose a ticket by its key.");
        }
    }

    record Create(TicketDraft draft) implements TicketChoice {

        public Create {
            Objects.requireNonNull(draft, "The captain chose to create the drafted ticket.");
        }
    }

    record DropTheTask() implements TicketChoice {
    }
}
