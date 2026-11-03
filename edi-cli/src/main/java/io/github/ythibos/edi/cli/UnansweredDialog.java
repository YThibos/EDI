package io.github.ythibos.edi.cli;

import java.util.regex.Pattern;

final class UnansweredDialog {

    private static final Pattern DIALOG_TOOL = Pattern.compile("\"tool_name\"\\s*:\\s*\"AskUserQuestion\"");
    private static final Pattern GIVEN_ANSWERS = Pattern.compile("\"(?:answers|annotations)\"\\s*:");

    private UnansweredDialog() {
    }

    static boolean isIn(String hookPayload) {
        return DIALOG_TOOL.matcher(hookPayload).find() && !GIVEN_ANSWERS.matcher(hookPayload).find();
    }
}
