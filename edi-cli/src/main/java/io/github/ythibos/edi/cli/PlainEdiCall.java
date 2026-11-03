package io.github.ythibos.edi.cli;

import java.util.regex.Pattern;

final class PlainEdiCall {

    private static final Pattern BASH_TOOL = Pattern.compile("\"tool_name\"\\s*:\\s*\"Bash\"");
    private static final Pattern PLAIN_EDI_COMMAND = Pattern.compile("\"command\"\\s*:\\s*\"edi(?: [A-Za-z0-9=._-]++)*+\"");
    private static final Pattern AN_EDI_WAIT = Pattern.compile("\"command\"\\s*:\\s*\"edi wait[ \"]");
    private static final Pattern ONE_WAIT_ALONE = Pattern.compile("\"command\"\\s*:\\s*\"edi wait\"");

    private PlainEdiCall() {
    }

    static boolean isIn(String hookPayload) {
        return BASH_TOOL.matcher(hookPayload).find() && PLAIN_EDI_COMMAND.matcher(hookPayload).find() && !startsTheWakeStream(hookPayload);
    }

    private static boolean startsTheWakeStream(String hookPayload) {
        return AN_EDI_WAIT.matcher(hookPayload).find() && !ONE_WAIT_ALONE.matcher(hookPayload).find();
    }
}
