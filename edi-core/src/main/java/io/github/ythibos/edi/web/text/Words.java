package io.github.ythibos.edi.web.text;

import java.util.Locale;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.AsciiCharacters;

public final class Words {

    private Words() {
    }

    public static String ofType(String typeName) {
        return typeName.chars()
                .mapToObj(character -> AsciiCharacters.isUpperCaseLetter(character)
                        ? " " + Character.toString(character).toLowerCase(Locale.ROOT)
                        : Character.toString(character))
                .collect(Collectors.joining())
                .strip();
    }

    public static String counted(String heading, int count) {
        return heading + " (" + count + ")";
    }

    public static String quantity(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    public static String indented(String text, String indent) {
        return text.lines().map(line -> line.isBlank() ? "" : indent + line).collect(Collectors.joining("\n"));
    }
}
