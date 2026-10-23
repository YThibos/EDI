package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.domain.worker.ErrorStop;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class ClaudeTranscript {

    private static final int TAIL_BYTES = 256 * 1024;
    private static final List<String> LIMIT_OPENINGS = List.of("You've hit your ", "You've reached your ");
    private static final String SEPARATOR = " · ";
    private static final String CONTEXT_LIMIT_OPENING = "Prompt is too long";
    private static final String STOP_HOOK_SUMMARY = "stop_hook_summary";
    private static final String TURN_END = "turn_duration";
    private static final String UNKNOWN_ERROR = "unknown";

    private final JsonMapper json;

    ClaudeTranscript(JsonMapper json) {
        this.json = json;
    }

    Optional<UsageLimit> usageLimitStop(Path transcript, Instant since) {
        return lastConversationalRecord(transcript)
                .filter(ClaudeTranscript::isAnApiError)
                .filter(record -> writtenAtOrAfter(record, since))
                .flatMap(ClaudeTranscript::usageLimitIn);
    }

    Optional<Instant> contextLimitStop(Path transcript, Instant since) {
        return lastConversationalRecord(transcript)
                .filter(ClaudeTranscript::isAnApiError)
                .filter(ClaudeTranscript::refusesAPromptTooLong)
                .flatMap(ClaudeTranscript::writtenAt)
                .filter(at -> !at.isBefore(since));
    }

    Optional<Instant> missedStop(Path transcript, Instant since) {
        List<JsonNode> latest = lastRecordsWhere(transcript, ClaudeTranscript::marksTheTurn, 2);
        if (!endsAfterAFailedStopHook(latest)) {
            return Optional.empty();
        }
        return writtenAt(latest.getFirst()).filter(at -> !at.isBefore(since));
    }

    Optional<ErrorStop> errorStop(Path transcript, Instant since) {
        List<JsonNode> latest = lastRecordsWhere(transcript, ClaudeTranscript::marksTheTurn, 2);
        if (!endsAfterAnApiError(latest)) {
            return Optional.empty();
        }
        return writtenAt(latest.getFirst()).filter(at -> !at.isBefore(since)).map(at -> new ErrorStop(at, errorOf(latest.getLast())));
    }

    TokenUsage tokensSpent(Path transcript, Instant since) {
        Set<String> counted = new HashSet<>();
        try (Stream<String> lines = Files.lines(transcript, StandardCharsets.UTF_8)) {
            return lines.map(this::parsed)
                    .flatMap(Optional::stream)
                    .filter(ClaudeTranscript::isAnAssistantMessage)
                    .filter(record -> writtenAtOrAfter(record, since))
                    .filter(record -> counted.add(record.path("message").path("id").asString("")))
                    .map(record -> usageOf(record.path("message").path("usage")))
                    .reduce(TokenUsage.NONE, TokenUsage::plus);
        } catch (NoSuchFileException absent) {
            return TokenUsage.NONE;
        } catch (IOException | UncheckedIOException unreadable) {
            throw new ProgramFailure("The transcript " + transcript + " cannot be read: " + unreadable.getMessage(), unreadable);
        }
    }

    private Optional<JsonNode> lastConversationalRecord(Path transcript) {
        List<String> lines = tailOf(transcript);
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            Optional<JsonNode> record = parsed(lines.get(index));
            if (record.isEmpty() || isConversational(record.get())) {
                return record;
            }
        }
        return Optional.empty();
    }

    private static boolean isAnApiError(JsonNode record) {
        return record.path("type").asString("").equals("assistant") && record.path("isApiErrorMessage").asBoolean(false);
    }

    private static boolean writtenAtOrAfter(JsonNode record, Instant since) {
        return writtenAt(record).filter(at -> !at.isBefore(since)).isPresent();
    }

    private static Optional<Instant> writtenAt(JsonNode record) {
        try {
            return Optional.of(Instant.parse(record.path("timestamp").asString("")));
        } catch (DateTimeException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<UsageLimit> usageLimitIn(JsonNode record) {
        String text = textOf(record.path("message").path("content"));
        return LIMIT_OPENINGS.stream()
                .filter(text::startsWith)
                .findFirst()
                .map(opening -> new UsageLimit(limitNameAfter(opening, text), resetOf(record)));
    }

    private static boolean refusesAPromptTooLong(JsonNode record) {
        return textOf(record.path("message").path("content")).startsWith(CONTEXT_LIMIT_OPENING);
    }

    private List<JsonNode> lastRecordsWhere(Path transcript, Predicate<JsonNode> sought, int count) {
        List<String> lines = tailOf(transcript);
        return IntStream.iterate(lines.size() - 1, index -> index >= 0, index -> index - 1)
                .mapToObj(lines::get)
                .map(this::parsed)
                .flatMap(Optional::stream)
                .filter(sought)
                .limit(count)
                .toList();
    }

    private static boolean marksTheTurn(JsonNode record) {
        return isConversational(record) || isASystemRecord(record, STOP_HOOK_SUMMARY) || isASystemRecord(record, TURN_END);
    }

    private static boolean endsAfterAFailedStopHook(List<JsonNode> latest) {
        return latest.size() == 2 && isASystemRecord(latest.getFirst(), TURN_END) && reportsAFailedHook(latest.getLast());
    }

    private static boolean isASystemRecord(JsonNode record, String subtype) {
        return record.path("type").asString("").equals("system") && record.path("subtype").asString("").equals(subtype);
    }

    private static boolean reportsAFailedHook(JsonNode summary) {
        return isASystemRecord(summary, STOP_HOOK_SUMMARY) && !summary.path("hookErrors").isEmpty();
    }

    private static boolean endsAfterAnApiError(List<JsonNode> latest) {
        return latest.size() == 2 && isASystemRecord(latest.getFirst(), TURN_END) && isAnApiError(latest.getLast());
    }

    private static String errorOf(JsonNode record) {
        String error = record.path("error").asString("");
        return error.isBlank() ? UNKNOWN_ERROR : error;
    }

    private Optional<JsonNode> parsed(String line) {
        try {
            JsonNode record = json.readTree(line);
            return record.isObject() ? Optional.of(record) : Optional.empty();
        } catch (JacksonException unparseable) {
            return Optional.empty();
        }
    }

    private static boolean isAnAssistantMessage(JsonNode record) {
        return record.path("type").asString("").equals("assistant") && record.path("message").path("usage").isObject();
    }

    private static TokenUsage usageOf(JsonNode usage) {
        return new TokenUsage(usage.path("input_tokens").asLong(0), usage.path("cache_read_input_tokens").asLong(0),
                usage.path("cache_creation_input_tokens").asLong(0), usage.path("output_tokens").asLong(0));
    }

    private static List<String> tailOf(Path transcript) {
        try (RandomAccessFile file = new RandomAccessFile(transcript.toFile(), "r")) {
            long start = Math.max(0, file.length() - TAIL_BYTES);
            byte[] tail = new byte[(int) (file.length() - start)];
            file.seek(start);
            file.readFully(tail);
            List<String> lines = new String(tail, StandardCharsets.UTF_8).lines().toList();
            return start > 0 && !lines.isEmpty() ? lines.subList(1, lines.size()) : lines;
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    private static boolean isConversational(JsonNode record) {
        String type = record.path("type").asString("");
        return type.equals("user") || type.equals("assistant");
    }

    private static String textOf(JsonNode content) {
        if (content.isString()) {
            return content.asString();
        }
        return StreamSupport.stream(content.spliterator(), false)
                .filter(block -> block.path("type").asString("").equals("text"))
                .map(block -> block.path("text").asString(""))
                .findFirst()
                .orElse("");
    }

    private static String limitNameAfter(String opening, String text) {
        String rest = text.substring(opening.length());
        int separator = rest.indexOf(SEPARATOR);
        String name = separator < 0 ? rest : rest.substring(0, separator);
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }

    private static Instant resetOf(JsonNode record) {
        JsonNode resetsAt = record.path("quotaLimits").path("resetsAt");
        if (!resetsAt.isNumber()) {
            resetsAt = record.path("apiErrorParams").path("rate_limit_info").path("resetsAt");
        }
        return resetsAt.isNumber() ? Instant.ofEpochSecond(resetsAt.asLong()) : null;
    }
}
