package io.github.ythibos.edi.application.supervision;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.DispatchRulesFile;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class CurrentRoutes {

    private final DispatchRulesFile rulesFile;
    private final MachineProfile machine;

    public CurrentRoutes(DispatchRulesFile rulesFile, MachineProfile machine) {
        this.rulesFile = rulesFile;
        this.machine = machine;
    }

    public Route of(Task task, Dispatch dispatch) {
        return rulesFile.validatedAgainst(machine.workers().models())
                .routeFor(task.intake().projects(), dispatch.category(), dispatch.captainsOverride());
    }
}
