package io.github.ythibos.edi.domain.spark;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

public record Spark(int number, String text, SparkAuthor author, UUID session, Focus focus, Instant caughtAt, SparkSorting sorting) {

    public Spark {
        if (number < 1) {
            throw new IllegalArgumentException("Sparks are numbered from 1: " + number);
        }
        Objects.requireNonNull(text);
        Objects.requireNonNull(author);
        Objects.requireNonNull(focus);
        Objects.requireNonNull(caughtAt);
    }

    public static Spark caught(int number, String text, SparkAuthor author, UUID session, Focus focus, Instant at) {
        String idea = Objects.requireNonNullElse(text, "").strip();
        if (idea.isEmpty()) {
            throw new Refusal(Rule.SPARK_SHAPE, "A spark says something: give the idea's text.");
        }
        return new Spark(number, idea, author, session, focus, at, null);
    }

    public boolean isOpen() {
        return sorting == null;
    }

    public Spark sorted(SparkSorting outcome) {
        if (!isOpen()) {
            throw new Refusal(Rule.SPARK_SORTED_ONCE, "Spark " + number + " was already " + sorting.outcome().word() + "; a spark is sorted once.");
        }
        return new Spark(number, text, author, session, focus, caughtAt, outcome);
    }
}
