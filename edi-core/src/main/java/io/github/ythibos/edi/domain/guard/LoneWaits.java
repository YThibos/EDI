package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class LoneWaits {

    private static final Set<String> THE_TWO_FORMS = Set.of("edi wait --follow", "edi wait");
    private static final String WAIT = "wait";

    private LoneWaits() {
    }

    static Optional<GuardRefusal> refusalFor(String script, List<ShellCommand> commands) {
        if (commands.stream().noneMatch(LoneWaits::isEdiWait) || THE_TWO_FORMS.contains(script.strip())) {
            return Optional.empty();
        }
        return Optional.of(new GuardRefusal(GuardRule.EDI_WAIT_NOT_ALONE,
                "edi wait runs alone, never chained, piped, redirected, sent to the background with &, disowned or under nohup,"
                        + " so no wake is printed where nobody reads it. Start the one wake stream with the Monitor tool running edi wait --follow"
                        + " and nothing else; where Monitor is unavailable, run edi wait alone as a background command."));
    }

    private static boolean isEdiWait(ShellCommand command) {
        List<String> arguments = command.argumentTexts();
        return command.runs("edi") && !arguments.isEmpty() && arguments.getFirst().equals(WAIT);
    }
}
