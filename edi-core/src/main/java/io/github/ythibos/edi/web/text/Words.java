package io.github.ythibos.edi.web.text;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.task.Wait;

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

    public static String waits(List<Wait> waits) {
        return "waits for " + waits.stream().map(Words::waitFor).collect(Collectors.joining(", "));
    }

    private static String waitFor(Wait wait) {
        return wait.words() + " (" + wait.stage().word() + ")";
    }
}
