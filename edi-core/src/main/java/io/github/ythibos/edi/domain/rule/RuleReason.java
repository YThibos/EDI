package io.github.ythibos.edi.domain.rule;

import java.util.List;
import java.util.regex.Pattern;

public record RuleReason(String text) {

    public static final int LONGEST = 200;

    private static final String ELLIPSIS = "...";
    private static final List<Redaction> REDACTIONS = List.of(
            new Redaction(Pattern.compile("\"[^\"]*\"|“[^”]*”"), "\"(quoted)\""),
            new Redaction(Pattern.compile("\\S*[/\\\\]\\S*"), "(path)"),
            new Redaction(Pattern.compile("\\S+@\\S+"), "(address)"),
            new Redaction(Pattern.compile("(?<![\\w.])\\d{1,3}(?:\\.\\d{1,3}){3}(?![\\w.])"), "(address)"),
            new Redaction(Pattern.compile("(?<![\\w.:-])[a-z0-9][a-z0-9.-]*:\\d{2,5}(?![\\w:])", Pattern.CASE_INSENSITIVE), "(address)"),
            new Redaction(Pattern.compile("(?<![\\w-])[A-Za-z0-9_+=-]{32,}(?![\\w-])"), "(token)"));
    private static final Pattern BLANKS = Pattern.compile("\\s+");

    public RuleReason {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A rule that fires says why in a short reason.");
        }
        if (text.length() > LONGEST) {
            throw new IllegalArgumentException("A rule's reason is at most " + LONGEST + " characters.");
        }
    }

    public static RuleReason of(String said) {
        return new RuleReason(shortened(redacted(BLANKS.matcher(said == null || said.isBlank() ? "no reason given" : said).replaceAll(" ").strip())));
    }

    private static String redacted(String said) {
        String reason = said;
        for (Redaction redaction : REDACTIONS) {
            reason = redaction.pattern().matcher(reason).replaceAll(redaction.replacement());
        }
        return reason;
    }

    private static String shortened(String reason) {
        return reason.length() <= LONGEST ? reason : reason.substring(0, LONGEST - ELLIPSIS.length()).stripTrailing() + ELLIPSIS;
    }

    private record Redaction(Pattern pattern, String replacement) {
    }
}
