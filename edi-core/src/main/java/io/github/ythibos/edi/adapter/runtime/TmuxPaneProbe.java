package io.github.ythibos.edi.adapter.runtime;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.worker.PaneState;

@Component
public class TmuxPaneProbe {

    private static final int LINES_READ = 30;
    private static final char PROMPT = '\u276F';
    private static final char RULE = '\u2500';
    private static final char NO_BREAK_SPACE = '\u00A0';
    private static final Map<PaneState, List<String>> DIALOG_SIGNS = Map.of(
            PaneState.DIALOG_TRUST, List.of("yes, i trust this folder", "is this a project you created or one you trust"),
            PaneState.DIALOG_SPEND_CAP, List.of("monthly spend limit"),
            PaneState.DIALOG_LIMIT_WAIT, List.of("wait for limit to reset", "wait here, then continue automatically"),
            PaneState.DIALOG_CONTINUE_AFTER_RESET, List.of("to continue after reset"));
    private static final List<PaneState> DIALOGS_IN_ORDER = List.of(
            PaneState.DIALOG_TRUST, PaneState.DIALOG_SPEND_CAP, PaneState.DIALOG_LIMIT_WAIT, PaneState.DIALOG_CONTINUE_AFTER_RESET);

    public PaneState classify(List<String> visibleLines) {
        List<String> lines = lastLinesOf(visibleLines);
        List<String> lowerCase = lines.stream().map(line -> line.toLowerCase(Locale.ROOT)).toList();
        if (showsTheExitPicker(lowerCase)) {
            return PaneState.DIALOG_EXIT_PICKER;
        }
        for (PaneState dialog : DIALOGS_IN_ORDER) {
            if (DIALOG_SIGNS.get(dialog).stream().anyMatch(sign -> lowerCase.stream().anyMatch(line -> line.contains(sign)))) {
                return dialog;
            }
        }
        if (asksForPermission(lowerCase)) {
            return PaneState.DIALOG_PERMISSION;
        }
        if (lowerCase.stream().anyMatch(line -> line.contains("esc to interrupt"))) {
            return PaneState.BUSY;
        }
        return showsAnEmptyComposer(lines) ? PaneState.IDLE_PROMPT : PaneState.UNKNOWN;
    }

    public boolean holdsInTheComposer(List<String> visibleLines, String text) {
        List<String> lines = lastLinesOf(visibleLines);
        String typed = PROMPT + " " + text;
        for (int index = 1; index < lines.size() - 1; index++) {
            if (lines.get(index).strip().equals(typed) && isRule(lines.get(index - 1)) && isRule(lines.get(index + 1))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> lastLinesOf(List<String> visibleLines) {
        List<String> stripped = visibleLines.stream().map(line -> line.replace(NO_BREAK_SPACE, ' ').stripTrailing()).toList();
        int end = stripped.size();
        while (end > 0 && stripped.get(end - 1).isEmpty()) {
            end--;
        }
        return stripped.subList(Math.max(0, end - LINES_READ), end);
    }

    private static boolean showsTheExitPicker(List<String> lines) {
        return lines.stream().anyMatch(line -> line.contains("background work is running"))
                && lines.stream().anyMatch(line -> line.contains("exit and stop tasks"));
    }

    private static boolean asksForPermission(List<String> lines) {
        boolean asks = lines.stream().map(String::strip).anyMatch(line -> line.startsWith("do you want to") && line.endsWith("?"));
        boolean offersChoices = lines.stream().anyMatch(line -> line.contains("esc to cancel"));
        return asks && offersChoices;
    }

    private static boolean showsAnEmptyComposer(List<String> lines) {
        for (int index = 1; index < lines.size() - 1; index++) {
            if (isEmptyPrompt(lines.get(index)) && isRule(lines.get(index - 1)) && isRule(lines.get(index + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmptyPrompt(String line) {
        String content = line.strip();
        return content.length() == 1 && content.charAt(0) == PROMPT;
    }

    private static boolean isRule(String line) {
        String content = line.strip();
        long ruleCharacters = content.chars().filter(character -> character == RULE).count();
        return !content.isEmpty() && content.charAt(0) == RULE && content.charAt(content.length() - 1) == RULE && ruleCharacters * 2 >= content.length();
    }
}
