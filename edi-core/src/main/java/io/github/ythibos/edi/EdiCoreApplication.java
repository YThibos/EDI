package io.github.ythibos.edi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.context.WebServerPortFileWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(proxyBeanMethods = false)
@EnableScheduling
public class EdiCoreApplication {

    public static void main(String[] arguments) {
        SpringApplication core = new SpringApplication(EdiCoreApplication.class);
        core.addListeners(new MoveIntoTheHome());
        core.run(arguments);
    }

    @Bean
    WebServerPortFileWriter corePortFile(EdiHome home) {
        return new WebServerPortFileWriter(home.corePortFile().toFile());
    }
}
