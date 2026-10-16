package io.github.ythibos.edi.domain.delivery;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AgentAttribution {

    private static final Pattern CO_AUTHOR = Pattern.compile("^\\s*co-authored-by:", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERATED_WITH = Pattern.compile("^[^\\p{Alnum}]*generated with", Pattern.CASE_INSENSITIVE);

    private AgentAttribution() {
    }

    public static Optional<String> firstIn(String text) {
        return text.lines().map(String::strip).filter(AgentAttribution::attributes).findFirst();
    }

    private static boolean attributes(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return CO_AUTHOR.matcher(lower).find() || GENERATED_WITH.matcher(lower).find();
    }
}
