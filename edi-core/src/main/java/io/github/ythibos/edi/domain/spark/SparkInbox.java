package io.github.ythibos.edi.domain.spark;

import java.util.List;

public record SparkInbox(List<Spark> open) {

    public SparkInbox {
        open = List.copyOf(open);
    }
}
