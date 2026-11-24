package io.github.ythibos.edi.application.plan;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.authority.CitedWords;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.plan.PlanGo;
import io.github.ythibos.edi.domain.rule.Rule;

@Component
public class CaptainsGo {

    private final DecisionStore decisions;
    private final CaptainsWordEvidence captainsWord;
    private final TransactionTemplate transaction;

    public CaptainsGo(DecisionStore decisions, CaptainsWordEvidence captainsWord, TransactionTemplate transaction) {
        this.decisions = decisions;
        this.captainsWord = captainsWord;
        this.transaction = transaction;
    }

    public PlanGo citedBy(Citation message, CallingSession calling, PlanGo named) {
        CitedWords cited = captainsWord.requireTyped(calling, message);
        return new PlanGo.CitedMessage(cited.source().session(), cited.source().number(), named.words());
    }

    public void requireGivenFor(Plan plan) {
        switch (plan.heading().go()) {
            case PlanGo.AnsweredDecision answered -> requirePromoteAuthorisedBy(plan, answered);
            case PlanGo.CitedMessage typed -> requireTypedByTheCaptain(plan, typed);
            case PlanGo.Uncited uncited -> throw withoutGo(plan, "its go, \"" + uncited.words() + "\", cites no stored word of the captain");
        }
    }

    private void requirePromoteAuthorisedBy(Plan plan, PlanGo.AnsweredDecision answered) {
        if (!authorisesPromote(answered)) {
            throw withoutGo(plan, "its go cites " + answered.decision() + ", which the captain has not answered with an option that authorises "
                    + CaptainWordAct.PROMOTE.word());
        }
    }

    private boolean authorisesPromote(PlanGo.AnsweredDecision answered) {
        try {
            return transaction.execute(_ -> decisions.get(answered.decision())).authorises(CaptainWordAct.PROMOTE);
        } catch (UnknownDecision unknown) {
            return false;
        }
    }

    private void requireTypedByTheCaptain(Plan plan, PlanGo.CitedMessage typed) {
        try {
            captainsWord.requireTyped(new CallingSession(typed.session()), new Citation.WholeMessage(typed.message()));
        } catch (Refusal notTyped) {
            throw withoutGo(plan, "its go cites message " + typed.message() + " of session " + typed.session() + ", which is not the captain's typed word: "
                    + notTyped.getMessage());
        }
    }

    private static Refusal withoutGo(Plan plan, String because) {
        return new Refusal(Rule.CAPTAINS_WORD, "The plan " + plan.key() + " dispatches no slice by itself, as " + because
                + "; a plan's go is a decision the captain answered with an option that authorises " + CaptainWordAct.PROMOTE.word()
                + ", or a message the captain typed, cited when the plan is entered.");
    }
}
