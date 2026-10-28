package io.github.ythibos.edi;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.core.Ordered;

public class CorePortTakenAnalyzer extends AbstractFailureAnalyzer<PortInUseException> implements Ordered {

    @Override
    protected FailureAnalysis analyze(Throwable failure, PortInUseException taken) {
        return new FailureAnalysis(description(taken.getPort()), action(taken.getPort()), taken);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String description(int port) {
        return "EDI core cannot listen on port " + port + ": another program holds it.";
    }

    private static String action(int port) {
        return "Stop the program that holds port " + port + ", or start the core with SERVER_PORT set to a free port.";
    }
}
