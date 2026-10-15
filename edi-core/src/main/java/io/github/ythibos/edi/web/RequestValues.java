package io.github.ythibos.edi.web;

import java.util.List;
import java.util.function.Function;

public final class RequestValues {

    private RequestValues() {
    }

    public static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The call needs its " + name + ".");
        }
        return value;
    }

    public static <T> T optional(String value, Function<String, T> parse) {
        return value == null || value.isBlank() ? null : parse.apply(value.strip());
    }

    public static <T> List<T> each(List<String> values, Function<String, T> parse) {
        return values == null ? List.of() : values.stream().map(String::strip).map(parse).toList();
    }

    public static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
