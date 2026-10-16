package io.github.ythibos.edi.application.brief;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class BriefTemplate implements RuntimeHintsRegistrar {

    static final String RESOURCE = "claude/worker/brief-template.md";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern(RESOURCE);
    }

    static String rendered(Map<String, String> values) {
        return template().lines()
                .filter(line -> !isAPlaceholderRenderedEmpty(line, values))
                .map(line -> filledIn(line, values))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static String template() {
        try (InputStream content = BriefTemplate.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (content == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the core's resources.");
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static boolean isAPlaceholderRenderedEmpty(String line, Map<String, String> values) {
        Matcher placeholder = PLACEHOLDER.matcher(line.strip());
        return placeholder.matches() && valueOf(placeholder.group(1), values).isEmpty();
    }

    private static String filledIn(String line, Map<String, String> values) {
        return PLACEHOLDER.matcher(line).replaceAll(found -> Matcher.quoteReplacement(valueOf(found.group(1), values)));
    }

    private static String valueOf(String name, Map<String, String> values) {
        String value = values.get(name);
        if (value == null) {
            throw new IllegalStateException("The brief template names {{" + name + "}}, which the brief does not fill in.");
        }
        return value;
    }
}
