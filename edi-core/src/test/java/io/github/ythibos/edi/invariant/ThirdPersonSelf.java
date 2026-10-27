package io.github.ythibos.edi.invariant;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import io.github.ythibos.edi.domain.voice.CaptainVoice;

final class ThirdPersonSelf {

    private static final String PARTS_NAMED_AFTER_EDI = "core|console|home|repository|session|session-start|return|wake|fleet|work|token|commit-msg|pre-push|ship|skin|project";
    private static final String PREPOSITIONS = "about|beside|for|from|in|into|of|on|outside|to|with";
    private static final Pattern EDI_AS_A_THIRD_PERSON = Pattern.compile(
            "(?<![\\w-])EDI['’]s\\b|(?<!\\b(?:" + PREPOSITIONS + ") )(?<![\\w-])EDI\\s+(?!(?:" + PARTS_NAMED_AFTER_EDI + ")(?![\\w-]))[a-z][\\w'’]*"
                    + "|\\bby EDI(?![\\w-])");

    private ThirdPersonSelf() {
    }

    static List<String> in(String text) {
        return EDI_AS_A_THIRD_PERSON.matcher(CaptainVoice.ownWording(text)).results().map(MatchResult::group).toList();
    }
}
