package io.github.ythibos.edi.application.brief;

import java.util.Comparator;
import java.util.List;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

final class BriefLength {

    private static final int MOST_LINES = 120;

    private BriefLength() {
    }

    static void requireWithinTheLimit(List<BriefSection> sections) {
        int lines = sections.stream().mapToInt(BriefSection::lines).sum();
        if (lines > MOST_LINES) {
            BriefSection longest = longestOf(sections);
            throw new Refusal(Rule.BRIEF_LENGTH, "Counting the captain's intent as one line and leaving out any research plan, the brief would run to " + lines
                    + " lines, over the " + MOST_LINES + " a brief holds; its longest section is " + longest.title() + ", at " + longest.lines()
                    + " lines: shorten it, or point the worker to a file in the task directory.");
        }
    }

    private static BriefSection longestOf(List<BriefSection> sections) {
        return sections.stream().max(Comparator.comparingInt(BriefSection::lines)).orElseThrow();
    }
}
