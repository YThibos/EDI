package io.github.ythibos.edi.domain.task;

import io.github.ythibos.edi.domain.decision.DecisionNumber;

public sealed interface Hold {

    String reason();

    default boolean repeats(Hold other) {
        return getClass() == other.getClass();
    }

    record OpenDecision(DecisionNumber number) implements Hold {

        @Override
        public String reason() {
            return number + " waits for its answer";
        }

        @Override
        public boolean repeats(Hold other) {
            return equals(other);
        }
    }

    record Deferral(String why, HoldUntil until) implements Hold {

        public Deferral {
            why = statedReason(why, "A deferral says why the task waits.");
        }

        @Override
        public String reason() {
            return "deferred: " + why;
        }
    }

    record Approval(String why) implements Hold {

        public Approval {
            why = statedReason(why, "An approval hold says what the captain approves.");
        }

        @Override
        public String reason() {
            return "waits for the captain's approval: " + why;
        }
    }

    record Outage(int entry) implements Hold {

        public Outage {
            if (entry < 1) {
                throw new IllegalArgumentException("The outage hold list is numbered from 1: " + entry);
            }
        }

        @Override
        public String reason() {
            return "number " + entry + " on the outage hold list";
        }
    }

    private static String statedReason(String why, String whenMissing) {
        String stated = why.strip();
        if (stated.isEmpty()) {
            throw new IllegalArgumentException(whenMissing);
        }
        return stated;
    }
}
