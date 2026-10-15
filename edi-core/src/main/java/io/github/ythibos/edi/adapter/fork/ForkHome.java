package io.github.ythibos.edi.adapter.fork;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record ForkHome(Path root, String backlog, String decisionSequence, String doneArchive, String noteArchive) {

    private static final String BACKLOG = "data/backlog.md";
    private static final String DECISION_SEQUENCE = "data/decision-seq";
    private static final String DONE_ARCHIVE = "data/done-archive.md";
    private static final String NOTE_ARCHIVE = "data/note-archive.md";
    private static final String DECISION_PREFIX = "D";
    private static final int MOST_DIGITS = 9;

    public static ForkHome at(Path root) {
        String backlog = present(root.resolve(BACKLOG))
                .orElseThrow(() -> new IllegalArgumentException(root + " is not a firstofficer home: it has no " + BACKLOG + "."));
        return new ForkHome(
                root,
                backlog,
                present(root.resolve(DECISION_SEQUENCE)).orElse(""),
                present(root.resolve(DONE_ARCHIVE)).orElse(""),
                present(root.resolve(NOTE_ARCHIVE)).orElse(""));
    }

    public SeenNumber highestDecisionNumber() {
        SeenNumber recorded = new SeenNumber(recordedDecisionSequence(), DECISION_SEQUENCE);
        return Stream.of(
                        recorded,
                        new SeenNumber(NumbersInText.highestAfter(DECISION_PREFIX, backlog), BACKLOG),
                        new SeenNumber(NumbersInText.highestAfter(DECISION_PREFIX, doneArchive), DONE_ARCHIVE),
                        new SeenNumber(NumbersInText.highestAfter(DECISION_PREFIX, noteArchive), NOTE_ARCHIVE))
                .reduce(recorded, (highest, seen) -> seen.value() > highest.value() ? seen : highest);
    }

    private static Optional<String> present(Path file) {
        try {
            return Optional.of(Files.readString(file));
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private int recordedDecisionSequence() {
        String recorded = decisionSequence.strip();
        return recorded.length() <= MOST_DIGITS && AsciiCharacters.isDigits(recorded) ? Integer.parseInt(recorded) : 0;
    }
}
