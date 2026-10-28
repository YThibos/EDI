package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "statusline", description = "Prints the status line of the primary's Claude Code session: reads Claude Code's statusLine JSON on"
        + " standard input and prints EDI's line for Claude Code.")
final class StatusLineCommand implements Callable<Integer> {

    private static final int SHOWN = 0;
    private static final int NOT_SHOWN = 1;

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            CoreReply reply = new CoreClient().post(edi.core(), "/api/statusline", payload(), "text/plain");
            return reply.succeeded() ? shown(reply.body()) : notShown(reply.body());
        } catch (IOException unreadable) {
            return notShown(unreadable.getMessage());
        } catch (CoreUnavailable unavailable) {
            return shown(unavailable.getMessage());
        }
    }

    private String payload() throws IOException {
        return new String(edi.input().readAllBytes(), StandardCharsets.UTF_8);
    }

    private int shown(String line) {
        PrintWriter out = spec.commandLine().getOut();
        out.println(line.stripTrailing());
        out.flush();
        return SHOWN;
    }

    private int notShown(String reason) {
        PrintWriter err = spec.commandLine().getErr();
        err.println("The status line could not be written: " + reason.stripTrailing());
        err.flush();
        return NOT_SHOWN;
    }
}
