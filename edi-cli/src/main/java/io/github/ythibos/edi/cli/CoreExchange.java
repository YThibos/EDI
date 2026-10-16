package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

final class CoreExchange {

    private static final int ANSWERED = 0;
    private static final int REFUSED = 1;
    private static final String STANDARD_INPUT = "-";
    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";

    @Option(names = "--json", description = "Print the core's JSON answer instead of text.")
    private boolean json;

    @Spec(Spec.Target.MIXEE)
    private CommandSpec command;

    static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    int get(String path) {
        return printed(core -> new CoreClient().get(core, path, accepted()));
    }

    int post(String path, JsonBody body) {
        return post(path, body.text());
    }

    int post(String path, String json) {
        return printed(core -> new CoreClient().post(core, path, json, accepted()));
    }

    String contentOf(String source) {
        try {
            return source.equals(STANDARD_INPUT)
                    ? new String(edi().input().readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(Path.of(source));
        } catch (IOException unreadable) {
            throw new ParameterException(command.commandLine(), source + " cannot be read: " + unreadable.getMessage());
        }
    }

    private int printed(Function<CoreConnection, CoreReply> call) {
        try {
            CoreReply reply = call.apply(CoreConnection.in(edi().home()));
            print(reply.succeeded() ? out() : err(), reply.body());
            return reply.succeeded() ? ANSWERED : REFUSED;
        } catch (CoreUnavailable unavailable) {
            err().println(unavailable.getMessage());
            return REFUSED;
        }
    }

    private String accepted() {
        return json ? JSON : TEXT;
    }

    private Edi edi() {
        return (Edi) command.root().userObject();
    }

    private PrintWriter out() {
        return command.commandLine().getOut();
    }

    private PrintWriter err() {
        return command.commandLine().getErr();
    }

    private static void print(PrintWriter writer, String body) {
        writer.print(body.endsWith("\n") ? body : body + "\n");
        writer.flush();
    }
}
