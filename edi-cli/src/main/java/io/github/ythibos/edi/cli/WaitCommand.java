package io.github.ythibos.edi.cli;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "wait", description = "Waits until something needs the primary, prints what it is and exits; with --follow it stays running as the"
        + " primary's one wake stream and prints each wake the moment it is raised.")
final class WaitCommand implements Callable<Integer> {

    private static final int WOKEN = 0;
    private static final int NOT_WAITING = 1;
    private static final int NOTHING_YET = 204;
    private static final String OFFERED_WAKES = "EDI-Wakes";
    private static final String WAIT_READ = "wake.wait-read";
    private static final Duration RETRY = Duration.ofSeconds(2);
    private static final Duration PATIENCE_WITH_AN_ABSENT_CORE = Duration.ofSeconds(60);

    @Option(names = "--follow", description = "Stay running as the primary's one wake stream and print each wake as it is raised;"
            + " start it alone with the Monitor tool.")
    private boolean follow;

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    private final UUID stream = UUID.randomUUID();
    private List<Long> printed = List.of();

    @Override
    public Integer call() {
        if (edi.claudeSession().idIfAny().isEmpty()) {
            return ended("edi wait runs inside the primary's Claude Code session, which names itself in CLAUDE_CODE_SESSION_ID.");
        }
        Optional<String> unread = edi.output().whyNobodyReadsIt();
        if (unread.isPresent()) {
            return refusedBecause(unread.get());
        }
        return follow ? followTheWakes() : waitForOneWake();
    }

    private int ended(String reason) {
        PrintWriter channel = follow ? spec.commandLine().getOut() : spec.commandLine().getErr();
        channel.println(follow ? "The wake stream ended: " + reason : reason);
        channel.flush();
        return NOT_WAITING;
    }

    private int refusedBecause(String unread) {
        String refusal = "edi wait refuses to start: " + unread + ", so every wake it took would be lost. Start the one wake stream with the Monitor"
                + " tool running edi wait --follow alone; where Monitor is unavailable, run edi wait alone as a background command.";
        new RuleFirings(edi).fired(WAIT_READ, RuleFirings.REFUSED, refusal);
        PrintWriter err = spec.commandLine().getErr();
        err.println(refusal);
        err.flush();
        return NOT_WAITING;
    }

    private int followTheWakes() {
        OptionalInt stopped = printTheNextWakes();
        while (stopped.isEmpty()) {
            stopped = printTheNextWakes();
        }
        return stopped.getAsInt();
    }

    private int waitForOneWake() {
        OptionalInt stopped = printTheNextWakes();
        if (stopped.isPresent()) {
            return stopped.getAsInt();
        }
        tellTheCoreTheStreamEnded();
        return WOKEN;
    }

    private OptionalInt printTheNextWakes() {
        Duration unanswered = Duration.ZERO;
        while (unanswered.compareTo(PATIENCE_WITH_AN_ABSENT_CORE) < 0) {
            Optional<CoreReply> reply = askTheCore();
            if (reply.isPresent() && reply.get().status() != NOTHING_YET) {
                return printReply(reply.get());
            }
            unanswered = reply.isPresent() ? Duration.ZERO : unanswered.plus(RETRY);
            if (reply.isEmpty() && !pausedForTheCore()) {
                return stoppedWith(ended("edi wait was interrupted."));
            }
        }
        return stoppedWith(ended("EDI core did not answer for a minute, so edi wait gave up; once the core runs, start it again."));
    }

    private void tellTheCoreTheStreamEnded() {
        sent(poll(true));
    }

    private Optional<CoreReply> askTheCore() {
        return sent(poll(false));
    }

    private Optional<CoreReply> sent(String poll) {
        try {
            CoreReply reply = new CoreClient().post(edi.core(), "/api/wait", poll, "text/plain");
            printed = List.of();
            return Optional.of(reply);
        } catch (CoreUnavailable unavailable) {
            return Optional.empty();
        }
    }

    private String poll(boolean ending) {
        return new JsonBody().with("stream", stream.toString())
                .with("pid", ProcessHandle.current().pid())
                .with("follows", follow)
                .withNumbers("printed", printed)
                .with("ending", ending)
                .text();
    }

    private OptionalInt printReply(CoreReply reply) {
        if (!reply.succeeded()) {
            return stoppedWith(ended(reply.body().strip()));
        }
        PrintWriter out = spec.commandLine().getOut();
        out.print(reply.body().endsWith("\n") ? reply.body() : reply.body() + "\n");
        if (out.checkError()) {
            return stoppedWith(NOT_WAITING);
        }
        printed = wakesNumberedIn(reply);
        return OptionalInt.empty();
    }

    private static OptionalInt stoppedWith(int exitCode) {
        return OptionalInt.of(exitCode);
    }

    private static List<Long> wakesNumberedIn(CoreReply reply) {
        return reply.header(OFFERED_WAKES)
                .map(numbers -> Arrays.stream(numbers.split(",")).map(String::strip).filter(number -> !number.isEmpty()).map(Long::valueOf).toList())
                .orElseGet(List::of);
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
