package io.github.ythibos.edi.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.publish.DenyList;
import io.github.ythibos.edi.domain.publish.RequestText;
import io.github.ythibos.edi.domain.publish.TextVerdict;

class RuleReasonTest {

    @Test
    void keepsAPlainReasonAsItWasSaid() {
        assertThat(RuleReason.of("fit-lamp is held: waiting on D4. Release it first.").text())
                .isEqualTo("fit-lamp is held: waiting on D4. Release it first.");
    }

    @Test
    void leavesOutPathsAddressesQuotesAndTokens() {
        RuleReason reason = RuleReason.of("A worker writes only inside /home/ada/.edi/worktrees/harness/fit-lamp, and C:\\Users\\ada\\notes.txt is outside;"
                + " the captain said \"ship it to Ada\", mail ada@example.org, host 10.1.2.3 or forge.internal:8443, token "
                + "ghp_abcdefghijklmnopqrstuvwxyz0123456789AB.");

        assertThat(reason.text()).isEqualTo("A worker writes only inside (path), and (path) is outside; the captain said \"(quoted)\", mail (address),"
                + " host (address) or (address), token (token).");
        assertThat(TextVerdict.of(new RequestText(reason.text(), ""), DenyList.empty(), List.of()).allowsPosting()).isTrue();
    }

    @Test
    void shortensALongReasonToTheFirstTwoHundredCharacters() {
        RuleReason reason = RuleReason.of("word ".repeat(100));

        assertThat(reason.text()).hasSize(RuleReason.LONGEST).endsWith("...");
    }

    @Test
    void saysSoWhenNoReasonWasGiven() {
        assertThat(RuleReason.of(" \n ").text()).isEqualTo("no reason given");
        assertThatThrownBy(() -> new RuleReason("")).isInstanceOf(IllegalArgumentException.class);
    }
}
