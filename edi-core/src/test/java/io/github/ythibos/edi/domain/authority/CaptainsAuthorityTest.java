package io.github.ythibos.edi.domain.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.TaskId;

class CaptainsAuthorityTest {

    private static final Instant OPENED = Instant.parse("2026-10-17T09:00:00Z");
    private static final UUID SESSION = UUID.randomUUID();
    private static final Exchange EXCHANGE = new Exchange(SESSION, OPENED);
    private static final TaskId LAMP = new TaskId("fit-lamp");

    @Test
    void takesTheLatestWordOnTheTaskFromThisSession() {
        List<Authorisation> words = List.of(
                new Authorisation(SESSION, LAMP, null, "Drop the lamp.", OPENED.plusSeconds(60)),
                new Authorisation(SESSION, LAMP, null, "Discard its work too.", OPENED.plusSeconds(120)),
                new Authorisation(UUID.randomUUID(), LAMP, null, "Discard everything.", OPENED.plusSeconds(180)),
                new Authorisation(SESSION, new TaskId("fit-door"), null, "Drop the door.", OPENED.plusSeconds(240)));

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, words, List.of())).contains(new CaptainsWord("Discard its work too."));
        assertThat(CaptainsAuthority.onTask(new TaskId("fit-hinge"), CaptainWordAct.DROP, EXCHANGE, words, List.of())).isEmpty();
    }

    @Test
    void aWordForEffortAboveHighAuthorisesThatActAloneAndAGeneralWordNeverLiftsEffort() {
        Authorisation general = new Authorisation(SESSION, LAMP, null, "Drop the lamp.", OPENED.plusSeconds(60));
        Authorisation forEffort = new Authorisation(SESSION, LAMP, CaptainWordAct.EFFORT_ABOVE_HIGH, "Fit the lamp at xhigh.", OPENED.plusSeconds(120));

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, List.of(forEffort), List.of())).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, List.of(general, forEffort), List.of()))
                .contains(new CaptainsWord("Drop the lamp."));
        assertThat(CaptainsAuthority.saidForActOnTask(LAMP, CaptainWordAct.EFFORT_ABOVE_HIGH, EXCHANGE, List.of(general))).isEmpty();
        assertThat(CaptainsAuthority.saidForActOnTask(LAMP, CaptainWordAct.EFFORT_ABOVE_HIGH, EXCHANGE, List.of(general, forEffort)))
                .contains(new CaptainsWord("Fit the lamp at xhigh."));
        assertThatThrownBy(() -> new Authorisation(SESSION, null, CaptainWordAct.EFFORT_ABOVE_HIGH, "Everything at xhigh.", OPENED))
                .hasMessage("The captain's word for effort-above-high is on one task.");
    }

    @Test
    void countsADecisionAnsweredSinceTheSessionOpened() {
        Decision before = discardAnsweredAt(OPENED.minusSeconds(1), 'A');
        Decision during = discardAnsweredAt(OPENED.plusSeconds(1), 'A');

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DISCARD_WORK, EXCHANGE, List.of(), List.of(before))).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DISCARD_WORK, EXCHANGE, List.of(), List.of(during)))
                .contains(new CaptainsWord("D58 A"));
    }

    @Test
    void countsADecisionOnlyForTheActItsChosenOptionNames() {
        Decision discardChosen = discardAnsweredAt(OPENED.plusSeconds(1), 'A');
        Decision discardDeclined = discardAnsweredAt(OPENED.plusSeconds(1), 'B');

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, List.of(), List.of(discardChosen))).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.PROMOTE, EXCHANGE, List.of(), List.of(discardChosen))).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DISCARD_WORK, EXCHANGE, List.of(), List.of(discardDeclined))).isEmpty();
    }

    @Test
    void keepsAWordOnTheHomeApartFromAWordOnATask() {
        List<Authorisation> words = List.of(new Authorisation(SESSION, LAMP, null, "Read the token for the lamp.", OPENED.plusSeconds(60)));

        assertThat(CaptainsAuthority.onTheHome(EXCHANGE, words)).isEmpty();
        assertThat(CaptainsAuthority.onTheHome(EXCHANGE, List.of(new Authorisation(SESSION, null, null, "Read the token.", OPENED.plusSeconds(60)))))
                .contains(new CaptainsWord("Read the token."));
    }

    private static Decision discardAnsweredAt(Instant at, char chosen) {
        DecisionCard card = new DecisionCard("Discard the lamp's work?", "",
                List.of(new DecisionOption(new OptionKey('A'), "Discard it", CaptainWordAct.DISCARD_WORK), new DecisionOption(new OptionKey('B'), "Keep it")),
                new Recommendation(new OptionKey('B'), "it may still be needed"));
        return Decision.raised(new DecisionNumber(58), LAMP, card, OPENED.minusSeconds(60))
                .answered(new CaptainAnswer("D58 " + chosen, new OptionKey(chosen), "", at, AnswerChannel.CONSOLE));
    }
}
