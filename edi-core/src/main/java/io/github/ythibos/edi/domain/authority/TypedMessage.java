package io.github.ythibos.edi.domain.authority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TypedMessage(UUID session, int number, String text, Instant sentAt) implements CaptainMessage {

    private static final Pattern PASTE_MARK = Pattern.compile("<(/?)pasted_content(?:\\s[^>]*)?>");
    private static final String CLOSING = "/";

    public TypedMessage {
        Objects.requireNonNull(session, "A captain's message is sent in a primary session.");
        Objects.requireNonNull(sentAt, "A captain's message records when it was sent.");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A typed message holds the captain's words.");
        }
    }

    @Override
    public String wholeText() {
        return text.strip();
    }

    @Override
    public List<MessageLine> lines() {
        List<MessageLine> lines = new ArrayList<>();
        boolean insideAPaste = false;
        for (String line : text.lines().toList()) {
            Matcher marks = PASTE_MARK.matcher(line);
            boolean pasted = insideAPaste;
            while (marks.find()) {
                pasted = true;
                insideAPaste = !CLOSING.equals(marks.group(1));
            }
            lines.add(new MessageLine(lines.size() + 1, line, pasted));
        }
        return lines;
    }
}
