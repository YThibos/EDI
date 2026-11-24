package io.github.ythibos.edi.domain.tracker;

import java.util.List;

public sealed interface TicketWalk {

    String from();

    List<String> through();

    record Arrived(String from, List<String> through) implements TicketWalk {

        public Arrived {
            through = List.copyOf(through);
        }

        public boolean wasAlreadyThere() {
            return through.isEmpty();
        }
    }

    record Stopped(String from, List<String> through, String because) implements TicketWalk {

        public Stopped {
            through = List.copyOf(through);
        }

        public String lastStatus() {
            return through.isEmpty() ? from : through.getLast();
        }
    }
}
