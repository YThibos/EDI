package io.github.ythibos.edi.domain.primary;

public final class PrimaryOutputStyle {

    public static final String NAME = "edi-primary";
    public static final String FILE_NAME = NAME + ".md";

    private PrimaryOutputStyle() {
    }

    public static String carrying(String prompt) {
        return """
                ---
                name: %s
                description: The primary's anchor and the captain's memory, written by edi up
                keep-coding-instructions: true
                ---

                %s""".formatted(NAME, prompt);
    }

    public static boolean holdsTheAnchor(String style, String anchor) {
        return style.contains(anchor.strip());
    }
}
