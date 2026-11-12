package io.github.ythibos.edi.adapter.store;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.health.CoreMeters;

final class MeasuredTransactions extends TransactionTemplate {

    private static final long serialVersionUID = 1L;

    private final transient CoreMeters meters;

    MeasuredTransactions(PlatformTransactionManager transactions, CoreMeters meters) {
        super(transactions);
        this.meters = meters;
    }

    @Override
    public <T> T execute(TransactionCallback<T> action) {
        return meters.storeTransaction(() -> super.execute(action));
    }
}
