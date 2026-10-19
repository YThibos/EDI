package io.github.ythibos.edi.domain.voice;

import java.util.Locale;
import java.util.regex.Pattern;

public final class InternalLabel {

    private final String label;
    private final String plainWords;
    private final Pattern wholeLabel;

    public InternalLabel(String label, String plainWords) {
        if (label.isBlank() || plainWords.isBlank()) {
            throw new IllegalArgumentException("An internal label names the plain words the captain hears instead.");
        }
        this.label = label;
        this.plainWords = plainWords;
        this.wholeLabel = Pattern.compile("(?<![\\w-])" + Pattern.quote(label.toLowerCase(Locale.ROOT)) + "(?![\\w-])");
    }

    public String label() {
        return label;
    }

    public String plainWords() {
        return plainWords;
    }

    boolean isIn(String comparableText) {
        return wholeLabel.matcher(comparableText).find();
    }

    @Override
    public String toString() {
        return label;
    }
}
