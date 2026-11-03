package io.github.ythibos.edi.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoneWaitsTest {

    private static final ShellGuard GUARD = new ShellGuard("edi", Set.of("main"));
    private static final String BASH = "Bash";
    private static final String MONITOR = "Monitor";

    @ParameterizedTest
    @ValueSource(strings = {
        "edi wait >/dev/null 2>&1 & disown",
        "edi wait &",
        "edi wait --follow &",
        "edi wait | tee wake.txt",
        "edi wait > wake.txt",
        "edi wait 2>&1",
        "edi wait >&-",
        "edi worker list; edi wait",
        "edi health && edi wait --follow",
        "edi wait || true",
        "nohup edi wait",
        "edi wait\nedi worker list",
        "cd ~/.edi && /usr/local/bin/edi wait",
        "bash -c 'edi wait --follow'",
        "(edi wait)",
        "echo $(edi wait)"
    })
    void refusesAnEdiWaitThatIsNotAlone(String command) {
        assertThat(GUARD.check(command, MONITOR)).as(command).hasValueSatisfying(refusal -> {
            assertThat(refusal.rule()).isEqualTo(GuardRule.EDI_WAIT_NOT_ALONE);
            assertThat(refusal.reason()).contains("the Monitor tool running edi wait --follow and nothing else");
        });
    }

    @Test
    void refusesTheWakeStreamOutsideTheMonitorTool() {
        assertThat(GUARD.check("edi wait --follow", BASH)).hasValueSatisfying(refusal -> {
            assertThat(refusal.rule()).isEqualTo(GuardRule.WAKE_STREAM_OUTSIDE_MONITOR);
            assertThat(refusal.reason()).contains("the Monitor tool running edi wait --follow and nothing else");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"edi wait --follow", "  edi wait --follow\n", "edi wait"})
    void letsTheWaitAloneRunUnderTheMonitorTool(String command) {
        assertThat(GUARD.check(command, MONITOR)).as(command).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"edi wait", "  edi wait\n", "echo edi wait", "edi worker list | grep wait", "grep 'edi wait' notes.txt"})
    void letsASingleWaitAloneAndEveryCommandThatOnlyNamesItPassAsABashCommand(String command) {
        assertThat(GUARD.check(command, BASH)).as(command).isEmpty();
    }
}
