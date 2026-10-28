package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "hook", description = "Answers a Claude Code hook: reads the hook's JSON on standard input and prints EDI's answer for Claude Code.")
final class HookCommand implements Callable<Integer> {

    private static final int ANSWERED = 0;
    private static final int NOT_ANSWERED = 1;
    private static final int BLOCKED = 2;
    private static final String GUARD = "pre-tool-use";

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Parameters(paramLabel = "EVENT", description = "session-start, user-prompt-submit, pre-tool-use, stop, stop-failure, notification or session-end.")
    private String event;

    @Override
    public Integer call() {
        String payload;
        try {
            payload = payload();
        } catch (IOException unreadable) {
            return unanswered(unreadable.getMessage());
        }
        try {
            CoreReply reply = new CoreClient().post(edi.core(), "/api/hooks/" + CoreExchange.encoded(event), payload, "application/json");
            return reply.succeeded() ? answered(reply.body()) : unanswered(reply.body().strip());
        } catch (CoreUnavailable unavailable) {
            return thePrimaryMayStillRunEdi(payload) ? ANSWERED : unanswered(unavailable.getMessage());
        }
    }

    private String payload() throws IOException {
        return new String(edi.input().readAllBytes(), StandardCharsets.UTF_8);
    }

    private boolean thePrimaryMayStillRunEdi(String payload) {
        return event.equals(GUARD) && !edi.runsForAWorker() && PlainEdiCall.isIn(payload);
    }

    private int answered(String answer) {
        PrintWriter out = spec.commandLine().getOut();
        out.print(answer);
        out.flush();
        return ANSWERED;
    }

    private int unanswered(String reason) {
        PrintWriter err = spec.commandLine().getErr();
        if (event.equals(GUARD)) {
            err.println("The guard could not check this tool call, so it is refused: " + reason);
            err.flush();
            return BLOCKED;
        }
        err.println("The " + event + " hook could not be taken: " + reason);
        err.flush();
        return NOT_ANSWERED;
    }
}
