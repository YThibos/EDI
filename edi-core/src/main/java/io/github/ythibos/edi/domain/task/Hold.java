package io.github.ythibos.edi.domain.task;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.rule.Rule;

public sealed interface Hold {

    HoldKind kind();

    String why();

    String reason();

    HoldUntil until();

    Instant since();

    default boolean repeats(Hold other) {
        return getClass() == other.getClass();
    }

    default boolean isReleasedByItsAnswer() {
        return false;
    }

    static Hold setByTheCaptain(HoldKind kind, String why, HoldUntil until, Instant since) {
        return switch (kind) {
            case DEFERRAL -> new Deferral(why, until, since);
            case APPROVAL -> new Approval(why, until, since);
            case DECISION -> throw new Refusal(Rule.TASK_HOLD, "A decision holds its task when it is raised with its card.");
            case OUTAGE -> throw new Refusal(Rule.TASK_HOLD, "An outage entry holds its task when its worker is lost in an outage.");
        };
    }

    record OpenDecision(DecisionNumber number, String title, Instant since) implements Hold {

        public OpenDecision {
            Objects.requireNonNull(since, () -> number + " holds its task from the moment it is raised.");
        }

        @Override
        public HoldKind kind() {
            return HoldKind.DECISION;
        }

        @Override
        public String why() {
            return number + " " + title;
        }

        @Override
        public String reason() {
            return number + " waits for its answer";
        }

        @Override
        public HoldUntil until() {
            return HoldUntil.captainsWord();
        }

        @Override
        public boolean repeats(Hold other) {
            return other instanceof OpenDecision decision && decision.number().equals(number);
        }

        @Override
        public boolean isReleasedByItsAnswer() {
            return true;
        }
    }

    record Deferral(String why, HoldUntil until, Instant since) implements Hold {

        public Deferral {
            why = statedReason(why, "A deferral says why the task waits.");
            Objects.requireNonNull(since, "A deferral holds its task from the moment it is set.");
        }

        @Override
        public HoldKind kind() {
            return HoldKind.DEFERRAL;
        }

        @Override
        public String reason() {
            return "deferred: " + why;
        }
    }

    record Approval(String why, HoldUntil until, Instant since) implements Hold {

        public Approval {
            why = statedReason(why, "An approval hold says what the captain approves.");
            Objects.requireNonNull(since, "An approval hold holds its task from the moment it is set.");
        }

        @Override
        public HoldKind kind() {
            return HoldKind.APPROVAL;
        }

        @Override
        public String reason() {
            return "waits for the captain's approval: " + why;
        }
    }

    record Outage(int entry, String lostWhen, Instant since) implements Hold {

        public Outage {
            if (entry < 1) {
                throw new IllegalArgumentException("The outage hold list is numbered from 1: " + entry);
            }
            lostWhen = statedReason(lostWhen, "An outage hold says how its worker was lost.");
            Objects.requireNonNull(since, "An outage hold holds its task from the moment it is listed.");
        }

        @Override
        public HoldKind kind() {
            return HoldKind.OUTAGE;
        }

        @Override
        public String why() {
            return reason();
        }

        @Override
        public String reason() {
            return lostWhen + "; number " + entry + " on the outage hold list waits for the captain to resume it or start it fresh";
        }

        @Override
        public HoldUntil until() {
            return HoldUntil.captainsWord();
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
