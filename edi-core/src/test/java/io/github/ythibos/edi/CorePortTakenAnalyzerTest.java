package io.github.ythibos.edi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Tag("adapter")
@ExtendWith(OutputCaptureExtension.class)
class CorePortTakenAnalyzerTest {

    @Test
    void aTakenPortStopsTheCoreWithThePortAndTheSettingThatMovesIt(CapturedOutput output) throws IOException {
        try (ServerSocket holder = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            assertThatThrownBy(() -> startAWebServerOn(holder.getLocalPort())).isInstanceOf(RuntimeException.class);

            assertThat(output).contains("EDI core cannot listen on port " + holder.getLocalPort() + ": another program holds it.")
                    .contains("Stop the program that holds port " + holder.getLocalPort() + ", or start the core with SERVER_PORT set to a free port.");
        }
    }

    private static void startAWebServerOn(int port) {
        SpringApplication application = new SpringApplication(AWebServer.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.run("--server.port=" + port).close();
    }

    @Configuration(proxyBeanMethods = false)
    static class AWebServer {

        @Bean
        TomcatServletWebServerFactory webServer(Environment environment) {
            TomcatServletWebServerFactory webServer = new TomcatServletWebServerFactory(environment.getRequiredProperty("server.port", Integer.class));
            webServer.setAddress(InetAddress.getLoopbackAddress());
            return webServer;
        }
    }
}
