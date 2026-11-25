package io.github.ythibos.edi.domain.plan;

import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.decision.DecisionNumber;

public sealed interface PlanGo {

    static PlanGo named(String words) {
        String go = Objects.requireNonNullElse(words, "").strip();
        return DecisionNumber.isWritten(go) ? new AnsweredDecision(DecisionNumber.parse(go)) : new Uncited(go);
    }

    String words();

    record AnsweredDecision(DecisionNumber decision) implements PlanGo {

        public AnsweredDecision {
            Objects.requireNonNull(decision, "A go by decision names the decision.");
        }

        @Override
        public String words() {
            return decision.toString();
        }
    }

    record CitedMessage(UUID session, int message, String words) implements PlanGo {

        public CitedMessage {
            Objects.requireNonNull(session, "A go the captain typed names the primary session it was typed in.");
            if (message < 1) {
                throw new IllegalArgumentException("The captain's messages are numbered from 1: " + message);
            }
        }
    }

    record Uncited(String words) implements PlanGo {
    }
}
