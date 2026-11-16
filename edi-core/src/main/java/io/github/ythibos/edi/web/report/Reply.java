package io.github.ythibos.edi.web.report;

import java.util.Locale;
import java.util.regex.Pattern;

import io.github.ythibos.edi.domain.decision.DecisionNumber;

public final class Reply {

    private static final Pattern MARKUP = Pattern.compile("[*_`]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final String text;
    private final String plain;

    private Reply(String text) {
        this.text = text;
        this.plain = plainly(text);
    }

    public static Reply of(String text) {
        return new Reply(text == null ? "" : text);
    }

    boolean namesTheDecision(DecisionNumber number) {
        return Pattern.compile("(?<![A-Za-z0-9])" + number + "(?![0-9])").matcher(text).find();
    }

    boolean namesTheAddress(String address) {
        return text.contains(address);
    }

    boolean saysTheItem(String itemText) {
        return plain.contains(plainly(itemText));
    }

    private static String plainly(String text) {
        String unmarked = MARKUP.matcher(text).replaceAll("");
        return WHITESPACE.matcher(unmarked).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
