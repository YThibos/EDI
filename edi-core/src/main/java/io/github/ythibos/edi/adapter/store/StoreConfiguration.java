package io.github.ythibos.edi.adapter.store;

import java.io.IOException;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.CoreInstanceLock;
import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.health.CoreMeters;

@Configuration(proxyBeanMethods = false)
class StoreConfiguration {

    @Bean
    @DependsOn(CoreInstanceLock.BEAN)
    DataSource store(EdiHome home) throws IOException {
        return StoreFile.openedAt(home.store());
    }

    @Bean
    OneTransactionAtATime transactionManager(DataSource store) {
        return new OneTransactionAtATime(store);
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactions, CoreMeters meters) {
        return new MeasuredTransactions(transactions, meters);
    }
}
