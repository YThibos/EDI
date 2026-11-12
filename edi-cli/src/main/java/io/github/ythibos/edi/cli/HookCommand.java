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
    private static final String CORE_UNREACHABLE = "guard.core-unreachable";
    private static final String HOOK_NOT_TAKEN = "hook.not-taken";

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Parameters(paramLabel = "EVENT", description = "session-start, user-prompt-submit, pre-tool-use, post-tool-use, stop, stop-failure, notification or session-end.")
    private String event;

    @Override
    public Integer call() {
        String payload;
        try {
            payload = payload();
        } catch (IOException unreadable) {
            return unanswered(unreadable.getMessage(), RuleFirings.throughTheCore(edi));
        }
        try {
            CoreReply reply = new CoreClient().post(edi.core(), "/api/hooks/" + CoreExchange.encoded(event), payload, "application/json");
            return reply.succeeded() ? answered(reply.body()) : unanswered(reply.body().strip(), firingsAfter(reply));
        } catch (CoreUnavailable unavailable) {
            return withoutTheCore(payload, unavailable.getMessage());
        }
    }

    private String payload() throws IOException {
        return new String(edi.input().readAllBytes(), StandardCharsets.UTF_8);
    }

    private RuleFirings firingsAfter(CoreReply reply) {
        return reply.refusedUnderARule() ? RuleFirings.recordedByTheCore(edi) : RuleFirings.throughTheCore(edi);
    }

    private int unanswered(String reason, RuleFirings firings) {
        PrintWriter err = spec.commandLine().getErr();
        if (event.equals(GUARD)) {
            String refusal = "The guard could not check this tool call, so it is refused: " + reason;
            err.println(refusal);
            err.flush();
            firings.fired(CORE_UNREACHABLE, RuleFirings.REFUSED, refusal);
            return BLOCKED;
        }
        String warning = "The " + event + " hook could not be taken: " + reason;
        err.println(warning);
        err.flush();
        firings.fired(HOOK_NOT_TAKEN, RuleFirings.WARNED, warning);
        return NOT_ANSWERED;
    }

    private int answered(String answer) {
        PrintWriter out = spec.commandLine().getOut();
        out.print(answer);
        out.flush();
        return ANSWERED;
    }

    private int withoutTheCore(String payload, String reason) {
        RuleFirings spooled = RuleFirings.intoTheSpool(edi);
        return thePrimaryMayGoOnWithoutTheCore(payload) ? allowedWithoutTheCore(reason, spooled) : unanswered(reason, spooled);
    }

    private boolean thePrimaryMayGoOnWithoutTheCore(String payload) {
        return event.equals(GUARD) && !edi.runsForAWorker() && (PlainEdiCall.isIn(payload) || UnansweredDialog.isIn(payload));
    }

    private int allowedWithoutTheCore(String reason, RuleFirings spooled) {
        spooled.fired(CORE_UNREACHABLE, RuleFirings.ALLOWED, "A plain edi call or an unanswered dialog went ahead while " + reason);
        return ANSWERED;
    }
}
