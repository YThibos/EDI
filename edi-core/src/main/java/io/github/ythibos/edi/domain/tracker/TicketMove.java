package io.github.ythibos.edi.domain.tracker;

import java.util.List;

public sealed interface TicketMove {

    record Moved(String from, List<String> through) implements TicketMove {

        public Moved {
            through = List.copyOf(through);
            if (through.isEmpty()) {
                throw new IllegalArgumentException("A ticket that moved passed through at least one status.");
            }
        }

        public String to() {
            return through.getLast();
        }
    }

    record AlreadyThere(String status) implements TicketMove {
    }

    record AlreadyBeyond(String status) implements TicketMove {
    }

    record Untracked() implements TicketMove {
    }
}
