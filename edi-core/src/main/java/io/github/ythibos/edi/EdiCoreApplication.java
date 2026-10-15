package io.github.ythibos.edi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.context.WebServerPortFileWriter;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(proxyBeanMethods = false)
public class EdiCoreApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(EdiCoreApplication.class, arguments);
    }

    @Bean
    WebServerPortFileWriter corePortFile(EdiHome home) {
        return new WebServerPortFileWriter(home.corePortFile().toFile());
    }
}
