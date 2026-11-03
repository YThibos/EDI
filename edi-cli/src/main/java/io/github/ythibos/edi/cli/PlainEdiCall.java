package io.github.ythibos.edi.cli;

import java.util.regex.Pattern;

final class PlainEdiCall {

    private static final Pattern BASH_TOOL = Pattern.compile("\"tool_name\"\\s*:\\s*\"Bash\"");
    private static final Pattern PLAIN_EDI_COMMAND = Pattern.compile("\"command\"\\s*:\\s*\"edi(?: [A-Za-z0-9=._-]++)*+\"");
    private static final Pattern HOOK_COMMAND = Pattern.compile("\"command\"\\s*:\\s*\"edi(?: -[A-Za-z0-9=._-]*+)*+ hook[ \"]");

    private PlainEdiCall() {
    }

    static boolean isIn(String hookPayload) {
        return BASH_TOOL.matcher(hookPayload).find() && PLAIN_EDI_COMMAND.matcher(hookPayload).find() && !feedsAHook(hookPayload);
    }

    private static boolean feedsAHook(String hookPayload) {
        return HOOK_COMMAND.matcher(hookPayload).find();
    }
}
