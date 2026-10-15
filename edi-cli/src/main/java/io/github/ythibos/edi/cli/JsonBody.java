package io.github.ythibos.edi.cli;

import java.util.List;
import java.util.stream.Collectors;

final class JsonBody {

    private static final int FIRST_PRINTABLE = 0x20;

    private final StringBuilder json = new StringBuilder("{");

    JsonBody with(String name, String value) {
        if (value != null) {
            field(name).append(quoted(value));
        }
        return this;
    }

    JsonBody with(String name, List<String> values) {
        if (values != null && !values.isEmpty()) {
            field(name).append(values.stream().map(JsonBody::quoted).collect(Collectors.joining(",", "[", "]")));
        }
        return this;
    }

    String text() {
        return json + "}";
    }

    private StringBuilder field(String name) {
        if (json.length() > 1) {
            json.append(',');
        }
        return json.append(quoted(name)).append(':');
    }

    private static String quoted(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        text.chars().forEach(character -> quoted.append(escaped((char) character)));
        return quoted.append('"').toString();
    }

    private static String escaped(char character) {
        return switch (character) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> character < FIRST_PRINTABLE ? "\\u%04x".formatted((int) character) : String.valueOf(character);
        };
    }
}
