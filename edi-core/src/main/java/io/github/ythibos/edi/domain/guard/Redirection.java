package io.github.ythibos.edi.domain.guard;

import java.util.regex.Pattern;

public enum Redirection {
    NONE,
    DESCRIPTOR,
    INPUT,
    OUTPUT,
    HERE_STRING;

    private static final String DUPLICATION = "&";
    private static final String WRITING = ">";
    private static final Pattern A_DESCRIPTOR = Pattern.compile("\\d+-?|-");

    static Redirection through(String operator, String target) {
        if (operator.length() > 1 && operator.endsWith(DUPLICATION) && A_DESCRIPTOR.matcher(target).matches()) {
            return DESCRIPTOR;
        }
        return operator.contains(WRITING) ? OUTPUT : INPUT;
    }

    public boolean redirects() {
        return this != NONE;
    }

    public boolean writesAFile() {
        return this == OUTPUT;
    }
}
