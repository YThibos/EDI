package io.github.ythibos.edi.domain.delivery;

public record PipelineIntent(String text) {

    private static final String PARAGRAPH_BREAK = "\n\n";

    public PipelineIntent {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A pipeline intent says what the captain set out to accomplish.");
        }
    }

    public static PipelineIntent of(String captainsIntent, String craftRules) {
        String intent = captainsIntent.strip();
        return new PipelineIntent(craftRules == null ? intent : intent + PARAGRAPH_BREAK + craftRules.strip());
    }

    @Override
    public String toString() {
        return text;
    }
}
