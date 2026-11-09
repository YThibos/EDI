package io.github.ythibos.edi.application.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.RuleFirings;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.rule.RuleFired;

@Service
public class FiredRules {

    private static final Logger LOG = LoggerFactory.getLogger(FiredRules.class);

    private final RuleFirings firings;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public FiredRules(RuleFirings firings, Clock clock, TransactionTemplate transaction) {
        this.firings = firings;
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
}
