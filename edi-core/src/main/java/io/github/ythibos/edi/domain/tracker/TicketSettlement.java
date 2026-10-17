package io.github.ythibos.edi.domain.tracker;

import io.github.ythibos.edi.domain.project.WorkKey;

public sealed interface TicketSettlement {

    static TicketSettlement none() {
        return new Unasked();
    }

    record Unasked() implements TicketSettlement {
    }

    record Keyed(WorkKey key, boolean created) implements TicketSettlement {
    }

    record CreatedWithoutItsFields(WorkKey key, String because) implements TicketSettlement {
    }

    record Pending(String because) implements TicketSettlement {
    }

    record AskedAgain(String because) implements TicketSettlement {
    }
}
