package io.github.ythibos.edi.web.authority;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.authority.CaptainMessage;
import io.github.ythibos.edi.domain.authority.CaptainMessages;
import io.github.ythibos.edi.domain.authority.DialogAnswer;
import io.github.ythibos.edi.domain.authority.MessageLine;
import io.github.ythibos.edi.domain.authority.TypedMessage;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class CaptainMessagesText implements TextPresenter<CaptainMessages> {

    private static final int GLIMPSE = 72;
    private static final String ELLIPSIS = "...";

    private final Moments moments;

    CaptainMessagesText(Moments moments) {
        this.moments = moments;
    }

    @Override
    public Class<CaptainMessages> presents() {
        return CaptainMessages.class;
    }

    @Override
    public String text(CaptainMessages sent) {
        Table table = new Table("  ").line(Words.counted("The captain's messages in this session", sent.messages().size()));
        sent.messages().forEach(message -> table.row(String.valueOf(message.number()), moments.clockTime(message.sentAt()), kindOf(message),
                summaryOf(message)));
        return table.text();
    }

    private static String kindOf(CaptainMessage message) {
        return switch (message) {
            case TypedMessage _ -> "typed";
            case DialogAnswer _ -> "dialog answer";
        };
    }

    private static String summaryOf(CaptainMessage message) {
        return switch (message) {
            case TypedMessage typed -> glimpse(typed.wholeText()) + linesOf(typed);
            case DialogAnswer answer -> glimpse(answer.answer().chosen()) + notesOf(answer) + " - to: " + glimpse(answer.answer().question());
        };
    }

    private static String glimpse(String text) {
        String line = text.strip().lines().findFirst().orElse("");
        return line.length() <= GLIMPSE ? line : line.substring(0, GLIMPSE - ELLIPSIS.length()) + ELLIPSIS;
    }

    private static String linesOf(TypedMessage message) {
        List<String> notes = new ArrayList<>();
        if (message.lines().size() > 1) {
            notes.add(Words.quantity(message.lines().size(), "line"));
        }
        if (message.holdsPastedContent()) {
            notes.add("pasted " + pastedLinesOf(message));
        }
        return notes.isEmpty() ? "" : notes.stream().collect(Collectors.joining(", ", " (", ")"));
    }

    private static String pastedLinesOf(TypedMessage message) {
        int[] pasted = message.lines().stream().filter(MessageLine::pasted).mapToInt(MessageLine::number).toArray();
        List<String> stretches = new ArrayList<>();
        int start = 0;
        for (int index = 1; index <= pasted.length; index++) {
            if (index == pasted.length || pasted[index] != pasted[index - 1] + 1) {
                stretches.add(stretch(pasted[start], pasted[index - 1]));
                start = index;
            }
        }
        return (pasted.length == 1 ? "line " : "lines ") + String.join(", ", stretches);
    }

    private static String stretch(int first, int last) {
        return first == last ? String.valueOf(first) : first + "-" + last;
    }

    private static String notesOf(DialogAnswer answer) {
        return answer.answer().carriesNotes() ? " (with notes)" : "";
    }
}
