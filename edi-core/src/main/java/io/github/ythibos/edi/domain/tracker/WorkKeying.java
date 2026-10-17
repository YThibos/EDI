package io.github.ythibos.edi.domain.tracker;

import java.util.List;
import java.util.Objects;

public sealed interface WorkKeying {

    record Unkeyed() implements WorkKeying {
    }

    record Given(Ticket ticket) implements WorkKeying {

        public Given {
            Objects.requireNonNull(ticket, "A given key names its ticket.");
        }
    }

    record ToIssue(TicketDraft draft) implements WorkKeying {

        public ToIssue {
            Objects.requireNonNull(draft, "A key is issued for a drafted ticket.");
        }
    }

    record ToPropose(TicketDraft draft, List<Ticket> candidates) implements WorkKeying {

        public ToPropose {
            Objects.requireNonNull(draft, "A ticket is proposed from a draft.");
            candidates = List.copyOf(candidates);
        }
    }
}
