package io.github.ythibos.edi.application.brief;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class BriefTemplate implements RuntimeHintsRegistrar {

    static final String RESOURCE = "claude/worker/brief-template.md";

    private static final String SECTION_HEADING = "## ";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern(RESOURCE);
    }

    static String rendered(Map<String, String> values) {
        return renderedLines(template().lines().toList(), values).collect(Collectors.joining("\n", "", "\n"));
    }

    static List<BriefSection> sections(Map<String, String> values) {
        return templateSections().stream()
                .map(section -> new BriefSection(titleOf(filledIn(section.getFirst(), values)),
                        renderedLines(section, values).mapToInt(BriefTemplate::physicalLinesOf).sum()))
                .toList();
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

    private static Stream<String> renderedLines(List<String> lines, Map<String, String> values) {
        return lines.stream()
                .filter(line -> !isAPlaceholderRenderedEmpty(line, values))
                .map(line -> filledIn(line, values));
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

    private static List<List<String>> templateSections() {
        List<List<String>> sections = new ArrayList<>();
        for (String line : template().lines().toList()) {
            if (sections.isEmpty() || line.startsWith(SECTION_HEADING)) {
                sections.add(new ArrayList<>());
            }
            sections.getLast().add(line);
        }
        return sections;
    }

    private static String titleOf(String heading) {
        return heading.replaceFirst("^#+ ", "");
    }

    private static int physicalLinesOf(String renderedLine) {
        return renderedLine.split("\n", -1).length;
    }
}
