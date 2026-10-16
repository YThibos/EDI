package io.github.ythibos.edi.domain;

public final class OperationalText {

    private static final String INVISIBLE_SEPARATOR = "\u2063";
    private static final String MARK = "EDI_OP: ";

    private OperationalText() {
    }

    public static String marked(String text) {
        return INVISIBLE_SEPARATOR + MARK + text;
    }

    public static String typed(String text) {
        return MARK + text;
    }
}
