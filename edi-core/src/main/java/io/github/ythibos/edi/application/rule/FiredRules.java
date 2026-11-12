package io.github.ythibos.edi.application.rule;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.RuleFirings;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.rule.RuleFired;

@Service
public class FiredRules {

    private static final Logger LOG = LoggerFactory.getLogger(FiredRules.class);

    private final RuleFirings firings;
    private final Duration kept;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public FiredRules(RuleFirings firings, @Value("${edi.rules.kept:P400D}") Duration kept, Clock clock, TransactionTemplate transaction) {
        this.firings = firings;
        this.kept = kept;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void record(RuleFired firing) {
        try {
            transaction.executeWithoutResult(_ -> firings.record(firing, clock.now()));
        } catch (DataAccessException unrecorded) {
            LOG.warn("The firing of {} could not be recorded, and what it {} stands: {}", firing.rule().id(), firing.outcome().word(),
                    unrecorded.getMessage());
        }
    }

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void forgetTheFiringsPastTheirKeeping() {
        Instant now = clock.now();
        transaction.executeWithoutResult(_ -> firings.forgetBefore(now.minus(kept)));
    }
}
