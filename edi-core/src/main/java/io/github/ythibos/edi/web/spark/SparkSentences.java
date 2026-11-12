package io.github.ythibos.edi.web.spark;

import io.github.ythibos.edi.domain.spark.Focus;
import io.github.ythibos.edi.domain.spark.Spark;
import io.github.ythibos.edi.domain.spark.SparkSorting;

public final class SparkSentences {

    private SparkSentences() {
    }

    public static String caughtFromTheCaptainsMessage(Spark spark) {
        return "The captain's message is a spark: you caught it as spark " + spark.number() + ", " + focusOf(spark.focus())
                + ", and it waits for the weekly gardening. Acknowledge it in one line and carry on with the work in hand;"
                + " do not act on the idea itself.";
    }

    static String caught(Spark spark) {
        return "Caught spark " + spark.number() + " from the " + spark.author().word() + ", " + focusOf(spark.focus())
                + "; it waits for the weekly gardening.";
    }

    static String sorted(Spark spark) {
        SparkSorting sorting = spark.sorting();
        return switch (sorting.outcome()) {
            case BACKLOGGED -> "Spark " + spark.number() + " is backlogged as " + sorting.item() + ".";
            case FOLDED -> "Spark " + spark.number() + " is folded into " + sorting.item() + ".";
            case DROPPED -> "Dropped spark " + spark.number() + ": " + sorting.reason();
        };
    }

    static String focusOf(Focus focus) {
        if (focus.task() != null) {
            return "focused on the task " + focus.task();
        }
        if (focus.project() != null) {
            return "focused on the project " + focus.project();
        }
        return "with no task or project in focus";
    }
}
