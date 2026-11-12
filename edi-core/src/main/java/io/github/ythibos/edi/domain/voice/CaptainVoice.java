package io.github.ythibos.edi.domain.voice;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

public final class CaptainVoice {

    public static final List<InternalLabel> INTERNAL_LABELS = List.of(
            new InternalLabel("worktree", "isolated copy"),
            new InternalLabel("worktrees", "isolated copies"),
            new InternalLabel("primary checkout", "your local copy"),
            new InternalLabel("local-main", "your local branch"),
            new InternalLabel("teardown", "cleanup"),
            new InternalLabel("watcher", "monitoring"),
            new InternalLabel("heartbeat", "monitoring"),
            new InternalLabel("wake", "notification"),
            new InternalLabel("wakes", "notifications"),
            new InternalLabel("crewmate", "worker"),
            new InternalLabel("crewmates", "workers"),
            new InternalLabel("second mate", "worker"),
            new InternalLabel("ask-user", "the approval the validation asks for"),
            new InternalLabel("needs-decision", "the decision itself"),
            new InternalLabel("fix-review", "the review finding"),
            new InternalLabel("checks-passed", "the checks pass"),
            new InternalLabel("fail-closed", "stops safely when something goes wrong"),
            new InternalLabel("fails closed", "stops safely when something goes wrong"),
            new InternalLabel("fail-open", "steps aside and lets the work continue"),
            new InternalLabel("fails open", "steps aside and lets the work continue"),
            new InternalLabel("fail loudly", "reports the missing requirement"),
            new InternalLabel("status file", "the record"),
            new InternalLabel("task id", "the task's name"),
            new InternalLabel("EDI_OP", "nothing: your own marker never reaches the captain"));

    private static final Pattern SPOKEN_AS_IS = Pattern.compile("`[^`]*`|\"[^\"]*\"|“[^”]*”|https?://\\S+|\\S*/\\S*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private CaptainVoice() {
    }

    public static List<InternalLabel> internalLabelsIn(String captainFacing) {
        String comparable = ownWording(captainFacing).toLowerCase(Locale.ROOT);
        return INTERNAL_LABELS.stream().filter(label -> label.isIn(comparable)).toList();
    }

    public static String ownWording(String text) {
        return WHITESPACE.matcher(SPOKEN_AS_IS.matcher(text).replaceAll(" ")).replaceAll(" ");
    }

    public static void requireOutcomes(String what, String captainFacing) {
        internalLabelsIn(captainFacing).stream().findFirst().ifPresent(label -> {
            throw new Refusal(Rule.CAPTAIN_VOICE, what + " speaks to the captain in outcomes: say \"" + label.plainWords() + "\" instead of \"" + label.label()
                    + "\", or quote a name in double quotes when it is one (I-22).");
        });
    }
}
