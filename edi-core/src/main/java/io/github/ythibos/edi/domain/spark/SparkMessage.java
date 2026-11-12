package io.github.ythibos.edi.domain.spark;

import java.util.List;
import java.util.Optional;

public final class SparkMessage {

    private static final List<String> OPENINGS = List.of("Spark:", "spark:");

    private SparkMessage() {
    }

    public static Optional<String> ideaIn(String message) {
        String opening = message.strip();
        return OPENINGS.stream()
                .filter(opening::startsWith)
                .map(prefix -> opening.substring(prefix.length()).strip())
                .filter(idea -> !idea.isEmpty())
                .findFirst();
    }
}
