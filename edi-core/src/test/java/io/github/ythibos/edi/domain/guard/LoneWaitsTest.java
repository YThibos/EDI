package io.github.ythibos.edi.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoneWaitsTest {

    private static final ShellGuard GUARD = new ShellGuard("edi", Set.of("main"));

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
        assertThat(GUARD.check(command)).as(command).hasValueSatisfying(refusal -> {
            assertThat(refusal.rule()).isEqualTo(GuardRule.EDI_WAIT_NOT_ALONE);
            assertThat(refusal.reason()).contains("the Monitor tool running edi wait --follow and nothing else");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"edi wait --follow", "edi wait", "  edi wait --follow\n", "echo edi wait", "edi worker list | grep wait", "grep 'edi wait' notes.txt"})
    void letsTheWaitAloneAndEveryCommandThatOnlyNamesItPass(String command) {
        assertThat(GUARD.check(command)).as(command).isEmpty();
    }
}
