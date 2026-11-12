package io.github.ythibos.edi.adapter.store;

import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;

public final class OneTransactionAtATime extends JdbcTransactionManager {

    private static final long serialVersionUID = 1L;

    private final ReentrantLock storeTurn = new ReentrantLock(true);

    public OneTransactionAtATime(DataSource store) {
        super(store);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        storeTurn.lock();
        try {
            super.doBegin(transaction, definition);
        } catch (RuntimeException | Error refused) {
            storeTurn.unlock();
            throw refused;
        }
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        try {
            super.doCleanupAfterCompletion(transaction);
        } finally {
            storeTurn.unlock();
        }
    }
}
