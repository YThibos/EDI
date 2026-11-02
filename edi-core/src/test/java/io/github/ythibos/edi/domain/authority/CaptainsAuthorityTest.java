package io.github.ythibos.edi.domain.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.authority.CaptainsAuthority.Heard;
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

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, said(words), List.of())).contains(new CaptainsWord("Discard its work too."));
        assertThat(CaptainsAuthority.onTask(new TaskId("fit-hinge"), CaptainWordAct.DROP, EXCHANGE, said(words), List.of())).isEmpty();
    }

    @Test
    void aWordForEffortAboveHighAuthorisesThatActAloneAndAGeneralWordNeverLiftsEffort() {
        Authorisation general = new Authorisation(SESSION, LAMP, null, "Drop the lamp.", OPENED.plusSeconds(60));
        Authorisation forEffort = new Authorisation(SESSION, LAMP, CaptainWordAct.EFFORT_ABOVE_HIGH, "Fit the lamp at xhigh.", OPENED.plusSeconds(120));

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, said(List.of(forEffort)), List.of())).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, said(List.of(general, forEffort)), List.of()))
                .contains(new CaptainsWord("Drop the lamp."));
        assertThat(CaptainsAuthority.saidForActOnTask(LAMP, CaptainWordAct.EFFORT_ABOVE_HIGH, EXCHANGE, said(List.of(general)))).isEmpty();
        assertThat(CaptainsAuthority.saidForActOnTask(LAMP, CaptainWordAct.EFFORT_ABOVE_HIGH, EXCHANGE, said(List.of(general, forEffort))))
                .contains(new CaptainsWord("Fit the lamp at xhigh."));
        assertThatThrownBy(() -> new Authorisation(SESSION, null, CaptainWordAct.EFFORT_ABOVE_HIGH, "Everything at xhigh.", OPENED))
                .hasMessage("The captain's word for effort-above-high is on one task.");
    }

    @Test
    void countsADecisionAnsweredSinceTheSessionOpened() {
        Decision before = discardAnsweredAt(OPENED.minusSeconds(1), 'A');
        Decision during = discardAnsweredAt(OPENED.plusSeconds(1), 'A');

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DISCARD_WORK, EXCHANGE, said(List.of()), List.of(before))).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DISCARD_WORK, EXCHANGE, said(List.of()), List.of(during)))
                .contains(new CaptainsWord("D58 A"));
    }

    @Test
    void countsADecisionOnlyForTheActItsChosenOptionNames() {
        Decision discardChosen = discardAnsweredAt(OPENED.plusSeconds(1), 'A');
        Decision discardDeclined = discardAnsweredAt(OPENED.plusSeconds(1), 'B');

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, said(List.of()), List.of(discardChosen))).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.PROMOTE, EXCHANGE, said(List.of()), List.of(discardChosen))).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DISCARD_WORK, EXCHANGE, said(List.of()), List.of(discardDeclined))).isEmpty();
    }

    @Test
    void keepsAWordOnTheHomeApartFromAWordOnATask() {
        List<Authorisation> words = List.of(new Authorisation(SESSION, LAMP, null, "Read the token for the lamp.", OPENED.plusSeconds(60)));

        assertThat(CaptainsAuthority.onTheHome(CaptainWordAct.TOUCH_A_SECRET, EXCHANGE, said(words))).isEmpty();
        assertThat(CaptainsAuthority.onTheHome(CaptainWordAct.TOUCH_A_SECRET, EXCHANGE,
                said(List.of(new Authorisation(SESSION, null, null, "Read the token.", OPENED.plusSeconds(60))))))
                .contains(new CaptainsWord("Read the token."));
    }

    @Test
    void countsADialogAnswerWhoseChosenOptionOpensWithTheActAndNamesTheTask() {
        DialogAnswer dropTheLamp = chosen("drop fit-lamp, it is obsolete", OPENED.plusSeconds(60));

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, chose(dropTheLamp), List.of()))
                .contains(new CaptainsWord("drop fit-lamp, it is obsolete"));
        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DISCARD_WORK, EXCHANGE, chose(dropTheLamp), List.of())).isEmpty();
        assertThat(CaptainsAuthority.onTask(new TaskId("fit"), CaptainWordAct.DROP, EXCHANGE, chose(dropTheLamp), List.of())).isEmpty();
        assertThat(CaptainsAuthority.onTask(new TaskId("fit-lamp-twice"), CaptainWordAct.DROP, EXCHANGE, chose(dropTheLamp), List.of())).isEmpty();
    }

    @Test
    void neverCountsADialogAnswerThatOnlyMentionsTheAct() {
        DialogAnswer keep = chosen("Do not drop fit-lamp", OPENED.plusSeconds(60));
        DialogAnswer dropped = chosen("dropped fit-lamp", OPENED.plusSeconds(60));
        DialogAnswer elsewhere = new DialogAnswer(UUID.randomUUID(), 1, new AnsweredQuestion("What now?", "drop fit-lamp", ""), OPENED.plusSeconds(60));

        assertThat(CaptainsAuthority.onTask(LAMP, CaptainWordAct.DROP, EXCHANGE, chose(keep, dropped, elsewhere), List.of())).isEmpty();
    }

    @Test
    void liftsEffortAndActsOnTheHomeOnADialogAnswerThatNamesThem() {
        DialogAnswer lift = chosen("Effort-above-high for fit-lamp at xhigh", OPENED.plusSeconds(60));
        DialogAnswer secret = chosen("touch-a-secret: read the forge token", OPENED.plusSeconds(120));

        assertThat(CaptainsAuthority.saidForActOnTask(LAMP, CaptainWordAct.EFFORT_ABOVE_HIGH, EXCHANGE, chose(lift, secret)))
                .contains(new CaptainsWord("Effort-above-high for fit-lamp at xhigh"));
        assertThat(CaptainsAuthority.onTheHome(CaptainWordAct.TOUCH_A_SECRET, EXCHANGE, chose(lift, secret)))
                .contains(new CaptainsWord("touch-a-secret: read the forge token"));
        assertThat(CaptainsAuthority.onTheHome(CaptainWordAct.STOP_RUNNING_WORKERS, EXCHANGE, chose(lift, secret))).isEmpty();
    }

    private static Heard said(List<Authorisation> words) {
        return new Heard(words, List.of());
    }

    private static Heard chose(DialogAnswer... answers) {
        return new Heard(List.of(), List.of(answers));
    }

    private static DialogAnswer chosen(String option, Instant at) {
        return new DialogAnswer(SESSION, 1, new AnsweredQuestion("What do we do with the lamp?", option, ""), at);
    }

    private static Decision discardAnsweredAt(Instant at, char chosen) {
        DecisionCard card = new DecisionCard("Discard the lamp's work?", "",
                List.of(new DecisionOption(new OptionKey('A'), "Discard it", CaptainWordAct.DISCARD_WORK), new DecisionOption(new OptionKey('B'), "Keep it")),
                new Recommendation(new OptionKey('B'), "it may still be needed"));
        return Decision.raised(new DecisionNumber(58), LAMP, card, OPENED.minusSeconds(60))
                .answered(new CaptainAnswer("D58 " + chosen, new OptionKey(chosen), "", at, AnswerChannel.CONSOLE));
    }
}
