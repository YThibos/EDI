package io.github.ythibos.edi.domain.publish;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.github.ythibos.edi.domain.mechanical.ProtectedFile;

final class PublishedDocument {

    private static final String MARKDOWN = ".md";
    private static final Pattern STORY_KEY_LINE = Pattern.compile("^key:[ \\t]*[A-Z][A-Z0-9]*-\\d+[ \\t\\r]*$", Pattern.MULTILINE);

    private PublishedDocument() {
    }

    static boolean isOne(String path, Function<String, String> contentAt) {
        return isMarkdown(path) && (ProtectedFile.DECISION_RECORD.covers(path) || isAStoryFile(contentAt.apply(path)));
    }

    private static boolean isMarkdown(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(MARKDOWN);
    }

    private static boolean isAStoryFile(String content) {
        return STORY_KEY_LINE.matcher(content).find();
    }
}
