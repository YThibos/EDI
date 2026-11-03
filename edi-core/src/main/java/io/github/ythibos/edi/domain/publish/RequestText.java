package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record RequestText(String title, String description) {

    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";

    public RequestText {
        title = Objects.requireNonNullElse(title, "");
        description = Objects.requireNonNullElse(description, "");
    }

    public static String placeOf(String part, int line) {
        return part.equals(TITLE) ? "the title" : "line " + line + " of the " + part;
    }

    List<TextLine> lines() {
        return Stream.concat(Stream.of(new TextLine(placeOf(TITLE, 1), title)), linesOfTheDescription()).toList();
    }

    private Stream<TextLine> linesOfTheDescription() {
        List<String> lines = description.lines().toList();
        return IntStream.range(0, lines.size()).mapToObj(index -> new TextLine(placeOf(DESCRIPTION, index + 1), lines.get(index)));
    }

    record TextLine(String place, String text) {
    }
}
