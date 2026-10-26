package io.github.ythibos.edi.adapter.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.ythibos.edi.domain.dispatch.Route;

final class AgentArguments {

    private static final String AGENT_ARGUMENTS = "agent_args_override";
    private static final String CLAUDE_CODE = "claude";

    private AgentArguments() {
    }

    static void routeClaudeCodeIn(Path globalConfiguration, Route route) {
        List<String> arguments = argumentsFor(route);
        PipelineConfiguration.changeEntry(globalConfiguration, AGENT_ARGUMENTS, agents -> arguments.forEach(agents.putArray(CLAUDE_CODE)::add));
    }

    private static List<String> argumentsFor(Route route) {
        List<String> arguments = new ArrayList<>(List.of("--model", route.model().value()));
        if (route.namesAnEffort()) {
            arguments.addAll(List.of("--effort", route.effort().word()));
        }
        return arguments;
    }
}
