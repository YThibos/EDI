package io.github.ythibos.edi.application.decision;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;

@Service
public class DecisionListQuery {

    private final DecisionStore decisions;
    private final TransactionTemplate transaction;

    public DecisionListQuery(DecisionStore decisions, TransactionTemplate transaction) {
        this.decisions = decisions;
        this.transaction = transaction;
    }

    public DecisionList open() {
        return transaction.execute(_ -> new DecisionList(true, decisions.open()));
    }

    public DecisionList all() {
        return transaction.execute(_ -> new DecisionList(false, decisions.all()));
    }
}
