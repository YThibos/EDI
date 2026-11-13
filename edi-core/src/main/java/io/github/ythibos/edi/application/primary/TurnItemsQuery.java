package io.github.ythibos.edi.application.primary;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.TurnItemStore;
import io.github.ythibos.edi.domain.report.Turn;

@Service
public class TurnItemsQuery {

    private final TurnItemStore turnItems;
    private final TransactionTemplate transaction;

    public TurnItemsQuery(TurnItemStore turnItems, TransactionTemplate transaction) {
        this.turnItems = turnItems;
        this.transaction = transaction;
    }

    public Turn recordedIn(UUID session) {
        return transaction.execute(_ -> turnItems.recordedIn(session));
    }
}
