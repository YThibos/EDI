package io.github.ythibos.edi.application.primary;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.application.access.OpenConsole;
import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.domain.primary.PrimarySession;

@Service
public class OpenTheConsoleOnRequest {

    private final OpenConsole console;
    private final PrimarySessionStore sessions;
    private final TransactionTemplate transaction;

    public OpenTheConsoleOnRequest(OpenConsole console, PrimarySessionStore sessions, TransactionTemplate transaction) {
        this.console = console;
        this.sessions = sessions;
        this.transaction = transaction;
    }

    public ConsoleOpening openFor(Optional<UUID> session, URI consoleAddress) {
        return showsTheSitrepItsStartOpened(session) ? ConsoleOpening.openedAtSessionStart(consoleAddress) : console.open(consoleAddress);
    }

    private boolean showsTheSitrepItsStartOpened(Optional<UUID> session) {
        return transaction.execute(_ -> session.flatMap(sessions::get)).filter(PrimarySession::showsTheSitrepItsStartOpened).isPresent();
    }
}
