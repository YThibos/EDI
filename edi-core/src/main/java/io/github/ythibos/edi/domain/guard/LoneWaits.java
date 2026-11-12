package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.rule.Rule;

final class LoneWaits {

    private static final String THE_WAKE_STREAM = "edi wait --follow";
    private static final String ONE_WAIT = "edi wait";
    private static final String MONITOR = "Monitor";
    private static final String WAIT = "wait";

    private LoneWaits() {
    }

    static Optional<GuardRefusal> refusalFor(String script, List<ShellCommand> commands, String tool) {
        String alone = script.strip();
        if (commands.stream().noneMatch(LoneWaits::isEdiWait) || startsAsIntended(alone, tool)) {
            return Optional.empty();
        }
        return Optional.of(alone.equals(THE_WAKE_STREAM) ? streamOutsideTheMonitor() : waitNotAlone());
    }

    private static boolean isEdiWait(ShellCommand command) {
        List<String> arguments = command.argumentTexts();
        return command.runs("edi") && !arguments.isEmpty() && arguments.getFirst().equals(WAIT);
    }

    private static boolean startsAsIntended(String alone, String tool) {
        return alone.equals(ONE_WAIT) || alone.equals(THE_WAKE_STREAM) && MONITOR.equals(tool);
    }

    private static GuardRefusal streamOutsideTheMonitor() {
        return new GuardRefusal(Rule.WAKE_STREAM_OUTSIDE_MONITOR,
                "edi wait --follow never ends, so only the Monitor tool, which turns each line it prints into a notification, can carry it;"
                        + " any other tool would take every wake into output nobody reads. Start the one wake stream with the Monitor tool running"
                        + " edi wait --follow and nothing else; where Monitor is unavailable, run edi wait alone as a background command.");
    }

    private static GuardRefusal waitNotAlone() {
        return new GuardRefusal(Rule.EDI_WAIT_NOT_ALONE,
                "edi wait runs alone, never chained, piped, redirected, sent to the background with &, disowned or under nohup,"
                        + " so no wake is printed where nobody reads it. Start the one wake stream with the Monitor tool running edi wait --follow"
                        + " and nothing else; where Monitor is unavailable, run edi wait alone as a background command.");
    }
}
