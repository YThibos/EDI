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

    public static HeldAnchor anchorHeldIn(String style, String anchor) {
        if (style.contains(anchor.strip())) {
            return HeldAnchor.CURRENT;
        }
        return style.lines().anyMatch(headingOf(anchor)::equals) ? HeldAnchor.OLDER : HeldAnchor.UNWRITTEN;
    }

    public static String headingOf(String anchor) {
        return anchor.lines().findFirst().orElseThrow();
    }
}
