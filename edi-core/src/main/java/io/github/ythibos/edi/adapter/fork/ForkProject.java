package io.github.ythibos.edi.adapter.fork;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record ForkProject(String name, String base) {

    private static final String BULLET = "- ";
    private static final String MODE_OPENING = " [";
    private static final String MODE_CLOSING = "] - ";
    private static final String BASE_WORD = "base ";
    private static final String ORIGIN_PREFIX = "origin/";
    private static final char BASE_END = ';';

    static List<ForkProject> allIn(String registry) {
        return registry.lines().map(ForkProject::fromLine).flatMap(Optional::stream).toList();
    }

    private static Optional<ForkProject> fromLine(String line) {
        int modeOpening = line.indexOf(MODE_OPENING);
        int modeClosing = line.indexOf(MODE_CLOSING, modeOpening + 1);
        if (!line.startsWith(BULLET) || modeOpening < BULLET.length() || modeClosing < 0) {
            return Optional.empty();
        }
        String name = line.substring(BULLET.length(), modeOpening);
        if (name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }
        return Optional.of(new ForkProject(name, baseIn(line.substring(modeClosing + MODE_CLOSING.length()))));
    }

    private static String baseIn(String description) {
        for (int at = description.indexOf(BASE_WORD); at >= 0; at = description.indexOf(BASE_WORD, at + 1)) {
            if (at == 0 || !AsciiCharacters.isLetterOrDigit(description.charAt(at - 1))) {
                return branchAt(description, at + BASE_WORD.length());
            }
        }
        return null;
    }

    private static String branchAt(String description, int start) {
        int end = description.indexOf(BASE_END, start);
        String branch = end < 0 ? "" : description.substring(start, end);
        if (branch.isEmpty() || branch.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        return branch.startsWith(ORIGIN_PREFIX) ? branch.substring(ORIGIN_PREFIX.length()) : branch;
    }
}
