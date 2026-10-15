package io.github.ythibos.edi.adapter.store;

import java.io.IOException;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ythibos.edi.EdiHome;

@Configuration(proxyBeanMethods = false)
class StoreConfiguration {

    @Bean
    DataSource store(EdiHome home) throws IOException {
        return StoreFile.openedAt(home.store());
    }
}
