package io.github.ythibos.edi.web.access;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.rule.FiredRules;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleFired;
import io.github.ythibos.edi.domain.rule.RuleOutcome;
import io.github.ythibos.edi.domain.rule.RuleReason;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CallerFirings {

    private static final Actor UNKNOWN_CALLER = new Actor("unknown caller");
    private static final Duration TURNED_AWAY_RECORDED_AT_MOST_ONCE_IN = Duration.ofMinutes(1);

    private final FiredRules firedRules;
    private final Clock clock;
    private final Map<TurnedAwayCaller, Instant> lastRecordedTurnedAway = new HashMap<>();

    public CallerFirings(FiredRules firedRules, Clock clock) {
        this.firedRules = firedRules;
        this.clock = clock;
    }

    public void refused(Rule rule, HttpServletRequest request, String reason) {
        firedRules.record(refusalOf(rule, request, reason));
    }

    public void fired(Rule rule, RuleOutcome outcome, Caller caller, String reason) {
        firedRules.record(firingOf(rule, outcome, caller, reason));
    }

    void turnedAway(Rule rule, HttpServletRequest request, String reason) {
        recordedAtMostOnceAMinute(refusalOf(rule, request, reason));
    }

    void turnedAway(Rule rule, Caller caller, String reason) {
        recordedAtMostOnceAMinute(firingOf(rule, RuleOutcome.REFUSED, caller, reason));
    }

    private static RuleFired refusalOf(Rule rule, HttpServletRequest request, String reason) {
        Object caller = request.getAttribute(Caller.ATTRIBUTE);
        if (caller instanceof Caller known) {
            return firingOf(rule, RuleOutcome.REFUSED, known, reason);
        }
        return new RuleFired(rule, RuleOutcome.REFUSED, UNKNOWN_CALLER, null, null, RuleReason.of(reason));
    }

    private static RuleFired firingOf(Rule rule, RuleOutcome outcome, Caller caller, String reason) {
        return new RuleFired(rule, outcome, caller.actor(), caller.task().orElse(null), caller.claudeSession().orElse(null), RuleReason.of(reason));
    }

    private void recordedAtMostOnceAMinute(RuleFired firing) {
        if (isTheFirstThisMinute(new TurnedAwayCaller(firing.rule(), firing.actor()), clock.now())) {
            firedRules.record(firing);
        }
    }

    private synchronized boolean isTheFirstThisMinute(TurnedAwayCaller turnedAway, Instant now) {
        Instant lastRecorded = lastRecordedTurnedAway.get(turnedAway);
        if (lastRecorded != null && now.isBefore(lastRecorded.plus(TURNED_AWAY_RECORDED_AT_MOST_ONCE_IN))) {
            return false;
        }
        lastRecordedTurnedAway.put(turnedAway, now);
        return true;
    }

    private record TurnedAwayCaller(Rule rule, Actor actor) {
    }
}
