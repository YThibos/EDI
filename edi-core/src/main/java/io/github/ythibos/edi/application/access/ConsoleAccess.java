package io.github.ythibos.edi.application.access;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.ConsoleAccessStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.console.ConsoleSession;
import io.github.ythibos.edi.domain.console.SignInLink;

@Service
public class ConsoleAccess {

    private final ConsoleAccessStore store;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ConsoleAccess(ConsoleAccessStore store, Clock clock, TransactionTemplate transaction) {
        this.store = store;
        this.clock = clock;
        this.transaction = transaction;
    }

    public String issueSignInLink() {
        String token = Tokens.drawn();
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            store.forgetExpiredBefore(now);
            store.save(SignInLink.issued(Tokens.digestOf(token), now));
        });
        return token;
    }

    public Optional<String> openSession(String signInToken) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Optional<SignInLink> link = store.signInLink(Tokens.digestOf(signInToken)).filter(issued -> issued.signsInAt(now));
            link.ifPresent(usable -> store.save(usable.used(now)));
            return link.map(_ -> sessionOpenedAt(now));
        });
    }

    public boolean admits(String sessionCookie) {
        Instant now = clock.now();
        return transaction.execute(_ -> store.session(Tokens.digestOf(sessionCookie))).filter(session -> session.isOpenAt(now)).isPresent();
    }

    private String sessionOpenedAt(Instant now) {
        String cookie = Tokens.drawn();
        store.save(ConsoleSession.opened(Tokens.digestOf(cookie), now));
        return cookie;
    }
}
