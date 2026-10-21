package io.github.ythibos.edi.application.primary;

import java.net.URI;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.application.access.OpenConsole;
import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.domain.primary.SessionStart;

@Service
public class OpenTheSitrepAtSessionStart {

    private final MachineProfile machine;
    private final OpenConsole console;

    public OpenTheSitrepAtSessionStart(MachineProfile machine, OpenConsole console) {
        this.machine = machine;
        this.console = console;
    }

    public Optional<ConsoleOpening> openFor(SessionStart start, URI consoleAddress) {
        return isWantedAt(start) ? Optional.of(console.open(consoleAddress)) : Optional.empty();
    }

    private boolean isWantedAt(SessionStart start) {
        return start.opensTheSitrep() && machine.opensTheSitrepAtSessionStart();
    }
}
