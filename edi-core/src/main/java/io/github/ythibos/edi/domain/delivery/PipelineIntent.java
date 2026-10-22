package io.github.ythibos.edi.domain.delivery;

public record PipelineIntent(String text) {

    private static final String PARAGRAPH_BREAK = "\n\n";
    private static final String CAPTAINS_INTENT_HEADING = "## Captain's intent";
    private static final String SPEC_HEADING = "## Spec";

    public PipelineIntent {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A pipeline intent says what the captain set out to accomplish.");
        }
    }

    public static PipelineIntent of(String captainsIntent, String spec, String craftRules) {
        requireTheCaptainsIntent(captainsIntent);
        String asked = section(CAPTAINS_INTENT_HEADING, captainsIntent) + PARAGRAPH_BREAK + section(SPEC_HEADING, spec);
        return new PipelineIntent(craftRules == null ? asked : asked + PARAGRAPH_BREAK + craftRules.strip());
    }

    private static void requireTheCaptainsIntent(String captainsIntent) {
        if (captainsIntent.isBlank()) {
            throw new IllegalArgumentException("A pipeline intent starts from what the captain set out to accomplish.");
        }
    }

    private static String section(String heading, String body) {
        return heading + PARAGRAPH_BREAK + body.strip();
    }

    @Override
    public String toString() {
        return text;
    }
}
