package io.github.ythibos.edi.application.primary;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.application.access.OpenConsole;
import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.primary.SessionStart;

@Service
public class OpenTheSitrepAtSessionStart {

    private final MachineProfile machine;
    private final OpenConsole console;
    private final PrimarySessionStore sessions;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OpenTheSitrepAtSessionStart(MachineProfile machine, OpenConsole console, PrimarySessionStore sessions, Clock clock,
            TransactionTemplate transaction) {
        this.machine = machine;
        this.console = console;
        this.sessions = sessions;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Optional<ConsoleOpening> openFor(UUID session, SessionStart start, URI consoleAddress) {
        return isWantedAt(start) ? Optional.of(recordedFor(session, console.open(consoleAddress))) : Optional.empty();
    }

    private boolean isWantedAt(SessionStart start) {
        return start.opensTheSitrep() && machine.opensTheSitrepAtSessionStart();
    }

    private ConsoleOpening recordedFor(UUID session, ConsoleOpening opening) {
        if (opening.opened()) {
            transaction.executeWithoutResult(_ -> sessions.get(session).ifPresent(started -> sessions.save(started.sitrepOpened(clock.now()))));
        }
        return opening;
    }
}
