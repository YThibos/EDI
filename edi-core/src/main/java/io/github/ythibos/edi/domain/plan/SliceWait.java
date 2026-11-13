package io.github.ythibos.edi.domain.plan;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ythibos.edi.domain.task.AwaitedStage;

public sealed interface SliceWait {

    static SliceWait parse(String words) {
        if (words.equals(None.WORDS)) {
            return new None();
        }
        Matcher after = After.WORDS.matcher(words);
        if (!after.matches()) {
            throw new IllegalArgumentException("A slice's wait is \"after N merged\", \"after N landed\" or \"none\", not \"" + words + "\".");
        }
        return new After(Integer.parseInt(after.group(1)), AwaitedStage.named(after.group(2)));
    }

    static SliceWait onTheSliceBefore(Optional<Integer> slice) {
        return slice.<SliceWait>map(before -> new After(before, AwaitedStage.MERGED)).orElseGet(None::new);
    }

    String words();

    record None() implements SliceWait {

        private static final String WORDS = "none";

        @Override
        public String words() {
            return WORDS;
        }
    }

    record After(int slice, AwaitedStage stage) implements SliceWait {

        private static final Pattern WORDS = Pattern.compile("after ([1-9][0-9]{0,5}) (merged|landed)");

        public After {
            if (slice < 1) {
                throw new IllegalArgumentException("A slice waits for a slice numbered from 1: " + slice);
            }
        }

        @Override
        public String words() {
            return "after " + slice + " " + stage.word();
        }
    }
}
