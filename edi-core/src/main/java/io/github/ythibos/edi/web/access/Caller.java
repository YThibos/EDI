package io.github.ythibos.edi.web.access;

import java.util.Optional;
import java.util.UUID;

import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.worker.WorkerId;

public sealed interface Caller {

    String ATTRIBUTE = Caller.class.getName();

    static Caller holdingTheHomeToken(UUID session, Standing standing) {
        return session != null && standing == Standing.STANDBY ? new Standby(session) : new Primary(session);
    }

    default Actor actor() {
        return switch (this) {
            case Primary _, Standby _ -> Actor.PRIMARY;
            case WorkerCaller caller -> Actor.worker(caller.worker());
            case Captain _ -> Actor.CAPTAIN;
        };
    }

    default void requirePrimary(String command) {
        if (this instanceof WorkerCaller) {
            throw new NotPermitted(command + " is the primary's to run; a worker reports through edi status and reads edi inbox.");
        }
        if (this instanceof Captain) {
            throw new NotPermitted(command + " is the primary's to run; the console answers decisions and asks for re-checks.");
        }
        if (this instanceof Standby) {
            throw new NotPermitted("This session is standby: the captain typed last in another EDI session, which is the primary now, so "
                    + command + " is refused here and this session only reads; it becomes the primary again when the captain types in it.");
        }
    }

    default WorkerId requireWorker(String command) {
        return switch (this) {
            case WorkerCaller caller -> caller.worker();
            case Primary _, Standby _, Captain _ ->
                    throw new NotPermitted(command + " is a worker's command: it runs under the token a worker gets at launch.");
        };
    }

    default void requireTheHomeToken(String command) {
        if (this instanceof WorkerCaller || this instanceof Captain) {
            throw new NotPermitted(command + " runs under the EDI home's own token, in the captain's terminal or the primary session.");
        }
    }

    default void requireThePrimaryOrTheCaptain(String request) {
        if (this instanceof WorkerCaller) {
            throw new NotPermitted(request + " is the primary's and the captain's to read; a worker reads its brief and edi inbox.");
        }
    }

    default void requireTheCaptain(String request) {
        if (!(this instanceof Captain)) {
            throw new NotPermitted(request + " is the captain's, given in the EDI console.");
        }
    }

    default AnswerChannel answeringChannel(String command) {
        if (this instanceof Captain) {
            return AnswerChannel.CONSOLE;
        }
        requirePrimary(command);
        return AnswerChannel.CHAT;
    }

    default Optional<UUID> claudeSession() {
        return switch (this) {
            case Primary primary -> Optional.ofNullable(primary.session());
            case Standby standby -> Optional.of(standby.session());
            case WorkerCaller _, Captain _ -> Optional.empty();
        };
    }

    default CallingSession callingSession() {
        return claudeSession().map(CallingSession::new).orElse(CallingSession.UNNAMED);
    }

    record Primary(UUID session) implements Caller {
    }

    record Standby(UUID session) implements Caller {
    }

    record WorkerCaller(WorkerId worker) implements Caller {
    }

    record Captain() implements Caller {
    }
}
