package io.github.ythibos.edi.adapter.fork;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.AsciiCharacters;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RegisterReflectionForBinding({ForkDecisionCard.class, ForkOption.class, ForkRecommendation.class})
public class ForkBacklogReader {

    private static final String DECISION_NUMBER = "Captain decision id: D";
    private static final String DECISION_CARD = "Captain decision card: ";
    private static final String RECORDED_ANSWER = "Captain decision:";
    private static final String HOLD_SET = "Captain hold set:";
    private static final int MOST_DIGITS = 9;

    private final JsonMapper json;

    public ForkBacklogReader(JsonMapper json) {
        this.json = json;
    }

    public ForkBacklog read(String markdown) {
        BacklogLines lines = BacklogLines.read(markdown);
        return new ForkBacklog(lines.entries().stream().map(this::entry).toList(), lines.setAside(), lines.strayLines());
    }

    private ForkEntry entry(BacklogLines.RawEntry raw) {
        List<String> body = raw.body().stream().map(String::strip).toList();
        return new ForkEntry(raw.line(), raw.section(), raw.id(), TailTags.of(raw.rest()), raw.body(), decisionIn(body), holdSetIn(body));
    }

    private ForkDecision decisionIn(List<String> body) {
        Integer number = firstAfter(body, DECISION_NUMBER)
                .filter(digits -> digits.length() <= MOST_DIGITS && AsciiCharacters.isDigits(digits))
                .map(Integer::valueOf)
                .orElse(null);
        Optional<String> card = firstAfter(body, DECISION_CARD);
        if (number == null && card.isEmpty()) {
            return null;
        }
        List<String> recordedAnswer = recordedAnswerIn(body);
        return card.map(json -> withCard(number, json, recordedAnswer))
                .orElseGet(() -> new ForkDecision(number, null, null, recordedAnswer));
    }

    private static Instant holdSetIn(List<String> body) {
        return body.stream()
                .filter(line -> !line.isEmpty())
                .findFirst()
                .filter(line -> line.startsWith(HOLD_SET))
                .map(line -> moment(line.substring(HOLD_SET.length()).strip()))
                .orElse(null);
    }

    private static Optional<String> firstAfter(List<String> body, String label) {
        return body.stream().filter(line -> line.startsWith(label)).findFirst().map(line -> line.substring(label.length()));
    }

    private static List<String> recordedAnswerIn(List<String> body) {
        int start = body.indexOf(RECORDED_ANSWER);
        if (start < 0) {
            return List.of();
        }
        int end = start + 1;
        while (end < body.size() && !body.get(end).isEmpty()) {
            end++;
        }
        return body.subList(start + 1, end);
    }

    private ForkDecision withCard(Integer number, String card, List<String> recordedAnswer) {
        try {
            return new ForkDecision(number, json.readValue(card, ForkDecisionCard.class), null, recordedAnswer);
        } catch (JacksonException unreadable) {
            return new ForkDecision(number, null, unreadable.getOriginalMessage(), recordedAnswer);
        }
    }

    private static Instant moment(String stamp) {
        try {
            return stamp.contains("T") ? Instant.parse(stamp) : LocalDate.parse(stamp).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }
}
