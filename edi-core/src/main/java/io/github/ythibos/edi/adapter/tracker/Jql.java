package io.github.ythibos.edi.adapter.tracker;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.tracker.TicketContext;

final class Jql {

    private static final int MOST_WORDS = 8;
    private static final int SHORTEST_WORD = 3;

    private Jql() {
    }

    static Optional<String> openTicketsMatching(TicketContext context) {
        String words = searchWordsOf(context.text());
        if (words.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("project = " + quoted(context.project().value()) + " AND statusCategory != Done AND text ~ " + quoted(words)
                + " ORDER BY updated DESC");
    }

    private static String searchWordsOf(String text) {
        return Arrays.stream(text.split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= SHORTEST_WORD)
                .limit(MOST_WORDS)
                .collect(Collectors.joining(" "));
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
