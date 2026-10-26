package io.github.ythibos.edi.domain.branch;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;

import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.project.WorkKey;

public record BranchName(BranchKind kind, WorkKey workKey, int followUp) {

    private static final char KIND_SEPARATOR = '/';
    private static final char NUMBER_SEPARATOR = '-';
    private static final int NO_FOLLOW_UP = 0;
    private static final int MOST_DIGITS = 9;
    private static final List<String> HARNESS_PREFIXES = List.of("fm/", "edi/");
    private static final String SHAPE = "feature|chore|research/<KEY>-<n>, such as feature/EDI-5";

    public BranchName {
        if (kind == null) {
            throw new IllegalArgumentException("A branch names the kind of its work: " + SHAPE + ".");
        }
        if (workKey == null) {
            throw new IllegalArgumentException("A branch carries its work key, so work without a key gets no branch: " + SHAPE + ".");
        }
        if (followUp < NO_FOLLOW_UP) {
            throw new IllegalArgumentException("A follow-up branch numbers its follow-ups from 1: " + followUp);
        }
    }

    public static BranchName forWork(BranchKind kind, WorkKey workKey) {
        return new BranchName(kind, workKey, NO_FOLLOW_UP);
    }

    public static BranchName forWorkBeside(BranchKind kind, WorkKey workKey, Collection<BranchName> branchesOfOtherTasks) {
        OptionalInt highestFollowUp = branchesOfOtherTasks.stream()
                .filter(branch -> branch.isFor(kind, workKey))
                .mapToInt(BranchName::followUp)
                .max();
        return highestFollowUp.isPresent() ? new BranchName(kind, workKey, highestFollowUp.getAsInt() + 1) : forWork(kind, workKey);
    }

    public static BranchName parse(String text) {
        refuseHarnessPrefixes(text);
        int kindEnd = text.indexOf(KIND_SEPARATOR);
        if (kindEnd < 1) {
            throw notABranchName(text);
        }
        BranchKind kind = kindIn(text.substring(0, kindEnd), text);
        return keyed(kind, text.substring(kindEnd + 1), text);
    }

    @Override
    public String toString() {
        String keyed = kind.word() + KIND_SEPARATOR + workKey;
        return followUp == NO_FOLLOW_UP ? keyed : keyed + NUMBER_SEPARATOR + followUp;
    }

    private boolean isFor(BranchKind otherKind, WorkKey otherKey) {
        return kind == otherKind && workKey.equals(otherKey);
    }

    private static void refuseHarnessPrefixes(String text) {
        HARNESS_PREFIXES.stream()
                .filter(text::startsWith)
                .findFirst()
                .ifPresent(prefix -> {
                    throw new IllegalArgumentException("A branch never starts with " + prefix + ": " + text + " should be " + SHAPE + ".");
                });
    }

    private static BranchKind kindIn(String word, String text) {
        try {
            return BranchKind.named(word);
        } catch (IllegalArgumentException unknownKind) {
            throw notABranchName(text);
        }
    }

    private static BranchName keyed(BranchKind kind, String keyAndFollowUp, String text) {
        int keyEnd = keyAndFollowUp.indexOf(NUMBER_SEPARATOR);
        if (keyEnd < 1) {
            throw notABranchName(text);
        }
        String[] numbers = keyAndFollowUp.substring(keyEnd + 1).split(String.valueOf(NUMBER_SEPARATOR), -1);
        if (numbers.length > 2 || !allDigits(numbers)) {
            throw notABranchName(text);
        }
        try {
            WorkKey workKey = WorkKey.parse(keyAndFollowUp.substring(0, keyEnd + 1 + numbers[0].length()));
            return new BranchName(kind, workKey, numbers.length == 2 ? Integer.parseInt(numbers[1]) : NO_FOLLOW_UP);
        } catch (IllegalArgumentException unkeyed) {
            throw notABranchName(text);
        }
    }

    private static boolean allDigits(String[] numbers) {
        return Arrays.stream(numbers).allMatch(number -> AsciiCharacters.isDigits(number) && number.length() <= MOST_DIGITS);
    }

    private static IllegalArgumentException notABranchName(String text) {
        return new IllegalArgumentException("A branch is " + SHAPE + ", not " + text + ".");
    }
}
