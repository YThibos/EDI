package io.github.ythibos.edi.cli;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

final class CaptainsWordsCited {

    private static final String STANDARD_INPUT = "-";

    @Spec(Spec.Target.MIXEE)
    private CommandSpec command;

    @Option(names = "--message", paramLabel = "N", description = "The number of the captain's message, as edi captain messages lists it.")
    private Integer message;

    @Option(names = "--line", paramLabel = "K", description = "One line of that message, counted from 1, when only that line is the word.")
    private Integer line;

    @Option(names = "--text", paramLabel = "WORDS", description = "The captain's words exactly as one stored message holds them, the whole message"
            + " or one whole line of it; - reads them from standard input.")
    private String text;

    JsonBody addedTo(JsonBody body, CoreExchange core) {
        requireOneCitation();
        return body.with("message", message).with("line", line).with("text", wordsFrom(core));
    }

    private void requireOneCitation() {
        if ((message == null) == (text == null)) {
            throw new ParameterException(command.commandLine(), "Cite the captain's words with --message, or with --text, one of the two.");
        }
        if (line != null && message == null) {
            throw new ParameterException(command.commandLine(), "--line cites one line of a message, so it takes --message.");
        }
    }

    private String wordsFrom(CoreExchange core) {
        return STANDARD_INPUT.equals(text) ? core.contentOf(STANDARD_INPUT) : text;
    }
}
