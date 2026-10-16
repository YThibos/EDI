package io.github.ythibos.edi.cli;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "wait", description = "Waits until something needs the primary, prints what it is and exits; the primary keeps one running in the background.")
final class WaitCommand implements Callable<Integer> {

    private static final int WOKEN = 0;
    private static final int NOT_WAITING = 1;
    private static final int NOTHING_YET = 204;
    private static final Duration RETRY = Duration.ofSeconds(2);
    private static final Duration PATIENCE_WITH_AN_ABSENT_CORE = Duration.ofSeconds(60);

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        if (edi.claudeSession().idIfAny().isEmpty()) {
            return notWaiting("edi wait runs inside the primary's Claude Code session, which names itself in CLAUDE_CODE_SESSION_ID.");
        }
        return untilAWake();
    }

    private int untilAWake() {
        Duration unanswered = Duration.ZERO;
        while (unanswered.compareTo(PATIENCE_WITH_AN_ABSENT_CORE) < 0) {
            Optional<CoreReply> reply = askTheCore();
            if (reply.isPresent() && reply.get().status() != NOTHING_YET) {
                return outcomeOf(reply.get());
            }
            unanswered = reply.isPresent() ? Duration.ZERO : unanswered.plus(RETRY);
            if (reply.isEmpty() && !pausedForTheCore()) {
                return notWaiting("edi wait was interrupted.");
            }
        }
        return notWaiting("EDI core did not answer for a minute, so edi wait gave up; start the core, then edi wait again.");
    }

    private Optional<CoreReply> askTheCore() {
        try {
            return Optional.of(new CoreClient().post(edi.core(), "/api/wait", "{}", "text/plain"));
        } catch (CoreUnavailable unavailable) {
            return Optional.empty();
        }
    }

    private int outcomeOf(CoreReply reply) {
        if (!reply.succeeded()) {
            return notWaiting(reply.body().strip());
        }
        PrintWriter out = spec.commandLine().getOut();
        out.print(reply.body().endsWith("\n") ? reply.body() : reply.body() + "\n");
        out.flush();
        return WOKEN;
    }

    private int notWaiting(String reason) {
        PrintWriter err = spec.commandLine().getErr();
        err.println(reason);
        err.flush();
        return NOT_WAITING;
    }

    private static boolean pausedForTheCore() {
        try {
            Thread.sleep(RETRY);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
