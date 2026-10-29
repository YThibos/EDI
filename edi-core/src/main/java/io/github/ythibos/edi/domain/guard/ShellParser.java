package io.github.ythibos.edi.domain.guard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class ShellParser {

    private static final int DEEPEST_NESTING = 12;
    private static final String COMMAND_SEPARATORS = ";&|()";
    private static final String REDIRECTION_CHARACTERS = "<>&|";
    private static final String WORD_BREAKS = " \t\n;&|()<>";
    private static final String DOUBLE_QUOTE_ESCAPES = "\"\\$`\n";
    private static final String HEREDOC_ESCAPES = "\\$`\n";
    private static final String EXTENDED_GLOB_OPENERS = "?*+@!";
    private static final String QUOTES_AND_ESCAPES = "'\"\\`$\n";

    private final String text;
    private final int nesting;
    private final boolean substitution;
    private final List<List<ShellWord>> commands = new ArrayList<>();
    private final Deque<Heredoc> heredocs = new ArrayDeque<>();
    private List<ShellWord> words = new ArrayList<>();
    private int position;
    private int openSubshells;
    private boolean closed;

    private ShellParser(String text, int start, int nesting, boolean substitution) {
        if (nesting > DEEPEST_NESTING) {
            throw new UnreadableCommand("The command nests more than " + DEEPEST_NESTING + " levels of commands inside commands.");
        }
        this.text = text;
        this.position = start;
        this.nesting = nesting;
        this.substitution = substitution;
    }

    static List<List<ShellWord>> simpleCommandsIn(String text, int nesting) {
        ShellParser parser = new ShellParser(text, 0, nesting, false);
        parser.parse();
        return parser.commands;
    }

    private void parse() {
        while (!atEnd() && !closed) {
            char character = current();
            if (character == ' ' || character == '\t') {
                position++;
            } else if (character == '\n') {
                endCommand();
                position++;
                readHeredocBodies();
            } else if (character == '#') {
                skipComment();
            } else if (startsRedirection()) {
                readRedirection();
            } else if (COMMAND_SEPARATORS.indexOf(character) >= 0) {
                readSeparator(character);
            } else {
                readWord(false);
            }
        }
        endCommand();
    }

    private void readSeparator(char separator) {
        endCommand();
        if (separator == ')' && substitution && openSubshells == 0) {
            closed = true;
        } else {
            openSubshells += subshellStepOf(separator);
            position++;
        }
    }

    private static int subshellStepOf(char separator) {
        return switch (separator) {
            case '(' -> 1;
            case ')' -> -1;
            default -> 0;
        };
    }

    private boolean startsRedirection() {
        char character = current();
        return character == '<' || character == '>' || character == '&' && peek(1) == '>';
    }

    private void readRedirection() {
        if ((current() == '<' || current() == '>') && peek(1) == '(') {
            position++;
            words.add(processSubstitution());
        } else if (text.startsWith("<<<", position)) {
            position += 3;
            readRedirectionTarget();
        } else if (text.startsWith("<<", position)) {
            position += 2;
            readHeredocDelimiter();
        } else {
            position++;
            while (!atEnd() && REDIRECTION_CHARACTERS.indexOf(current()) >= 0) {
                position++;
            }
            readRedirectionTarget();
        }
    }

    private ShellWord processSubstitution() {
        WrittenWord written = new WrittenWord();
        List<ShellCommand> substitutions = new ArrayList<>();
        readParenthesisedSubstitution(written, substitutions);
        return written.word(substitutions, false);
    }

    private void readRedirectionTarget() {
        skipBlanks();
        if (!atEnd() && WORD_BREAKS.indexOf(current()) < 0) {
            readWord(true);
        }
    }

    private void readHeredocDelimiter() {
        if (!atEnd() && current() == '-') {
            position++;
        }
        skipBlanks();
        StringBuilder delimiter = new StringBuilder();
        boolean quoted = false;
        while (!atEnd() && WORD_BREAKS.indexOf(current()) < 0) {
            char character = current();
            if (character == '\'' || character == '"' || character == '\\') {
                quoted = true;
            } else {
                delimiter.append(character);
            }
            position++;
        }
        heredocs.add(new Heredoc(delimiter.toString(), !quoted));
    }

    private void readHeredocBodies() {
        while (!heredocs.isEmpty() && !atEnd()) {
            Heredoc heredoc = heredocs.peek();
            int lineEnd = lineEndFrom(position);
            if (text.substring(position, lineEnd).strip().equals(heredoc.delimiter())) {
                heredocs.remove();
                position = Math.min(lineEnd + 1, text.length());
            } else if (heredoc.expanded()) {
                readExpandedBodyLine();
            } else {
                position = Math.min(lineEnd + 1, text.length());
            }
        }
    }

    private void readExpandedBodyLine() {
        WrittenWord written = new WrittenWord();
        List<ShellCommand> substitutions = new ArrayList<>();
        while (!atEnd() && current() != '\n') {
            readExpandedPart(written, substitutions, HEREDOC_ESCAPES);
        }
        position = Math.min(position + 1, text.length());
        if (!substitutions.isEmpty()) {
            commands.add(List.of(new WrittenWord().word(substitutions, false)));
        }
    }

    private void skipComment() {
        position = lineEndFrom(position);
    }

    private void readWord(boolean redirectionTarget) {
        WrittenWord written = new WrittenWord();
        List<ShellCommand> substitutions = new ArrayList<>();
        while (!atEnd() && (WORD_BREAKS.indexOf(current()) < 0 || startsAGlobGroup(written))) {
            readWordPart(written, substitutions);
        }
        words.add(written.word(substitutions, redirectionTarget || namesAFileDescriptor(written.text())));
    }

    private boolean startsAGlobGroup(WrittenWord written) {
        if (current() != '(' || !continuesAWord(written.text())) {
            return false;
        }
        int end = groupEnd(position);
        if (end < 0 && !blankBeforeAnythingUnreadable(position)) {
            throw new UnreadableCommand("The command attaches a group to a word that holds a quote, an escape or an expansion, so the guard cannot tell which files it globs.");
        }
        return end > 0 && text.substring(position, end).chars().noneMatch(Character::isWhitespace);
    }

    private static boolean continuesAWord(String before) {
        return !before.isEmpty() && !before.endsWith("=");
    }

    private boolean blankBeforeAnythingUnreadable(int open) {
        for (int at = open; at < text.length(); at++) {
            char character = text.charAt(at);
            if (character == ' ' || character == '\t') {
                return true;
            } else if (QUOTES_AND_ESCAPES.indexOf(character) >= 0) {
                return false;
            }
        }
        return false;
    }

    private boolean namesAFileDescriptor(String written) {
        boolean redirectedNext = !atEnd() && (current() == '<' || current() == '>');
        return redirectedNext && !written.isEmpty() && written.chars().allMatch(Character::isDigit);
    }

    private void readWordPart(WrittenWord written, List<ShellCommand> substitutions) {
        char character = current();
        if (character == '\\') {
            readEscaped(written);
        } else if (character == '\'') {
            readSingleQuoted(written);
        } else if (character == '"') {
            readDoubleQuoted(written, substitutions);
        } else if (character == '$' && peek(1) == '\'') {
            position++;
            readAnsiQuoted(written);
        } else if (character == '$' && peek(1) == '(' && peek(2) == '(') {
            readArithmetic(written);
        } else if (character == '$' && peek(1) == '(') {
            position++;
            readParenthesisedSubstitution(written, substitutions);
        } else if (character == '`') {
            readBacktickSubstitution(written, substitutions);
        } else if (EXTENDED_GLOB_OPENERS.indexOf(character) >= 0 && peek(1) == '(' && groupEnd(position + 1) > 0) {
            readGlobGroup(written, substitutions, position + 1);
        } else if (character == '(') {
            readGlobGroup(written, substitutions, position);
        } else {
            written.open(character);
            position++;
        }
    }

    private int groupEnd(int open) {
        int depth = 0;
        for (int at = open; at < text.length(); at++) {
            char character = text.charAt(at);
            if (character == '(') {
                depth++;
            } else if (character == ')' && --depth == 0) {
                return at + 1;
            } else if (QUOTES_AND_ESCAPES.indexOf(character) >= 0) {
                return -1;
            }
        }
        return -1;
    }

    private void readGlobGroup(WrittenWord written, List<ShellCommand> substitutions, int open) {
        int end = groupEnd(open);
        text.substring(position, end).chars().forEach(character -> written.open((char) character));
        substitutions.addAll(ShellScript.commandsIn(text.substring(open + 1, end - 1), nesting + 1));
        position = end;
    }

    private void readEscaped(WrittenWord written) {
        position++;
        if (!atEnd()) {
            if (current() != '\n') {
                written.quoted(current());
            }
            position++;
        }
    }

    private void readSingleQuoted(WrittenWord written) {
        int closing = text.indexOf('\'', position + 1);
        int end = closing < 0 ? text.length() : closing;
        written.quoted(text.substring(position + 1, end));
        position = Math.min(end + 1, text.length());
    }

    private void readAnsiQuoted(WrittenWord written) {
        position++;
        while (!atEnd() && current() != '\'') {
            if (current() == '\\' && position + 1 < text.length()) {
                position++;
            }
            written.quoted(current());
            position++;
        }
        position = Math.min(position + 1, text.length());
    }

    private void readDoubleQuoted(WrittenWord written, List<ShellCommand> substitutions) {
        position++;
        while (!atEnd() && current() != '"') {
            readExpandedPart(written, substitutions, DOUBLE_QUOTE_ESCAPES);
        }
        position = Math.min(position + 1, text.length());
    }

    private void readExpandedPart(WrittenWord written, List<ShellCommand> substitutions, String escapable) {
        char character = current();
        if (character == '\\' && position + 1 < text.length() && escapable.indexOf(peek(1)) >= 0) {
            position++;
            if (current() != '\n') {
                written.quoted(current());
            }
            position++;
        } else if (character == '$' && peek(1) == '{') {
            readParameter(written);
        } else if (character == '$' && peek(1) == '(' && peek(2) == '(') {
            readArithmetic(written);
        } else if (character == '$' && peek(1) == '(') {
            position++;
            readParenthesisedSubstitution(written, substitutions);
        } else if (character == '`') {
            readBacktickSubstitution(written, substitutions);
        } else if (character == '$') {
            written.open(character);
            position++;
        } else {
            written.quoted(character);
            position++;
        }
    }

    private void readParameter(WrittenWord written) {
        int closing = text.indexOf('}', position);
        int stop = closing < 0 ? text.length() : closing + 1;
        text.substring(position, stop).chars().forEach(character -> written.open((char) character));
        position = stop;
    }

    private void readArithmetic(WrittenWord written) {
        int end = text.indexOf("))", position + 3);
        int stop = end < 0 ? text.length() : end + 2;
        written.quoted(text.substring(position, stop));
        position = stop;
    }

    private void readParenthesisedSubstitution(WrittenWord written, List<ShellCommand> substitutions) {
        ShellParser inner = new ShellParser(text, position + 1, nesting + 1, true);
        inner.parse();
        written.quoted("$(" + text.substring(position + 1, inner.position) + ")");
        substitutions.addAll(ShellScript.commandsOf(inner.commands, nesting + 1));
        position = Math.min(inner.position + 1, text.length());
    }

    private void readBacktickSubstitution(WrittenWord written, List<ShellCommand> substitutions) {
        StringBuilder inner = new StringBuilder();
        position++;
        while (!atEnd() && current() != '`') {
            if (current() == '\\' && peek(1) == '`') {
                position++;
            }
            inner.append(current());
            position++;
        }
        position = Math.min(position + 1, text.length());
        written.quoted("`" + inner + "`");
        substitutions.addAll(ShellScript.commandsIn(inner.toString(), nesting + 1));
    }

    private void endCommand() {
        if (!words.isEmpty()) {
            commands.add(words);
            words = new ArrayList<>();
        }
    }

    private void skipBlanks() {
        while (!atEnd() && (current() == ' ' || current() == '\t')) {
            position++;
        }
    }

    private int lineEndFrom(int from) {
        int newline = text.indexOf('\n', from);
        return newline < 0 ? text.length() : newline;
    }

    private boolean atEnd() {
        return position >= text.length();
    }

    private char current() {
        return text.charAt(position);
    }

    private char peek(int ahead) {
        int at = position + ahead;
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private record Heredoc(String delimiter, boolean expanded) {
    }

    private static final class WrittenWord {

        private static final String EXPANSION_CHARACTERS = "\\*?[]{},~$@!+()|";

        private final StringBuilder text = new StringBuilder();
        private final StringBuilder pattern = new StringBuilder();

        void open(char character) {
            text.append(character);
            pattern.append(character);
        }

        void quoted(String characters) {
            characters.chars().forEach(character -> quoted((char) character));
        }

        void quoted(char character) {
            text.append(character);
            if (EXPANSION_CHARACTERS.indexOf(character) >= 0) {
                pattern.append('\\');
            }
            pattern.append(character);
        }

        String text() {
            return text.toString();
        }

        ShellWord word(List<ShellCommand> substitutions, boolean redirectionTarget) {
            return new ShellWord(text(), pattern.toString(), substitutions, redirectionTarget);
        }
    }
}
