package io.github.ythibos.edi.application.spark;

import java.time.Instant;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.SparkStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.spark.Spark;
import io.github.ythibos.edi.domain.spark.SparkInbox;
import io.github.ythibos.edi.domain.spark.SparkSorting;

@Service
public class SortSpark {

    private final SparkStore sparks;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SortSpark(SparkStore sparks, Clock clock, TransactionTemplate transaction) {
        this.sparks = sparks;
        this.clock = clock;
        this.transaction = transaction;
    }

    public SparkInbox inbox() {
        return transaction.execute(_ -> new SparkInbox(sparks.open()));
    }

    public Spark backlog(int number, WorkKey item) {
        return sorted(number, at -> SparkSorting.backlogged(item, at));
    }

    public Spark foldInto(int number, WorkKey item) {
        return sorted(number, at -> SparkSorting.foldedInto(item, at));
    }

    public Spark drop(int number, String reason) {
        return sorted(number, at -> SparkSorting.dropped(reason, at));
    }

    private Spark sorted(int number, Function<Instant, SparkSorting> outcome) {
        return transaction.execute(_ -> {
            Spark sorted = sparks.get(number).sorted(outcome.apply(clock.now()));
            sparks.sort(sorted);
            return sorted;
        });
    }
}
