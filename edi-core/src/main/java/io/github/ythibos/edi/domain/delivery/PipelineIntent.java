package io.github.ythibos.edi.domain.delivery;

import io.github.ythibos.edi.domain.project.WorkKey;

public record PipelineIntent(String text) {

    private static final String PARAGRAPH_BREAK = "\n\n";
    private static final String CAPTAINS_INTENT_HEADING = "## Captain's intent";
    private static final String SPEC_HEADING = "## Spec";
    private static final String COMMITS_HEADING = "## Commits";

    public PipelineIntent {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A pipeline intent says what the captain set out to accomplish.");
        }
    }

    public static PipelineIntent of(WorkKey workKey, String captainsIntent, String spec, String craftRules) {
        requireTheCaptainsIntent(captainsIntent);
        String asked = section(CAPTAINS_INTENT_HEADING, captainsIntent) + PARAGRAPH_BREAK + section(SPEC_HEADING, spec)
                + PARAGRAPH_BREAK + section(COMMITS_HEADING, keyedCommits(workKey));
        return new PipelineIntent(craftRules == null ? asked : asked + PARAGRAPH_BREAK + craftRules.strip());
    }

    private static void requireTheCaptainsIntent(String captainsIntent) {
        if (captainsIntent.isBlank()) {
            throw new IllegalArgumentException("A pipeline intent starts from what the captain set out to accomplish.");
        }
    }

    private static String keyedCommits(WorkKey workKey) {
        return "The pipeline starts every commit subject it writes, and the pull request's title, with the work key `" + workKey + "`.\n"
                + "A fix summary or a title it asks for is one short line that never starts with a work key: not `" + workKey
                + "`, which the pipeline would then write twice, and no other key either.";
    }

    private static String section(String heading, String body) {
        return heading + PARAGRAPH_BREAK + body.strip();
    }

    @Override
    public String toString() {
        return text;
    }
}
