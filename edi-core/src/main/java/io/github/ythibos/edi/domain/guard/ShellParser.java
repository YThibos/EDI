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
        StringBuilder written = new StringBuilder();
        List<ShellCommand> substitutions = new ArrayList<>();
        readParenthesisedSubstitution(written, substitutions);
        return new ShellWord(written.toString(), substitutions, false);
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
        StringBuilder written = new StringBuilder();
        List<ShellCommand> substitutions = new ArrayList<>();
        while (!atEnd() && current() != '\n') {
            readExpandedPart(written, substitutions, HEREDOC_ESCAPES);
        }
        position = Math.min(position + 1, text.length());
        if (!substitutions.isEmpty()) {
            commands.add(List.of(new ShellWord("", substitutions, false)));
        }
    }

    private void skipComment() {
        position = lineEndFrom(position);
    }

    private void readWord(boolean redirectionTarget) {
        StringBuilder written = new StringBuilder();
        List<ShellCommand> substitutions = new ArrayList<>();
        while (!atEnd() && WORD_BREAKS.indexOf(current()) < 0) {
            readWordPart(written, substitutions);
        }
        words.add(new ShellWord(written.toString(), substitutions, redirectionTarget || namesAFileDescriptor(written)));
    }

    private boolean namesAFileDescriptor(StringBuilder written) {
        boolean redirectedNext = !atEnd() && (current() == '<' || current() == '>');
        return redirectedNext && !written.isEmpty() && written.chars().allMatch(Character::isDigit);
    }

    private void readWordPart(StringBuilder written, List<ShellCommand> substitutions) {
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
        } else {
            written.append(character);
            position++;
        }
    }

    private void readEscaped(StringBuilder written) {
        position++;
        if (!atEnd()) {
            if (current() != '\n') {
                written.append(current());
            }
            position++;
        }
    }

    private void readSingleQuoted(StringBuilder written) {
        int closing = text.indexOf('\'', position + 1);
        int end = closing < 0 ? text.length() : closing;
        written.append(text, position + 1, end);
        position = Math.min(end + 1, text.length());
    }

    private void readAnsiQuoted(StringBuilder written) {
        position++;
        while (!atEnd() && current() != '\'') {
            if (current() == '\\' && position + 1 < text.length()) {
                position++;
            }
            written.append(current());
            position++;
        }
        position = Math.min(position + 1, text.length());
    }

    private void readDoubleQuoted(StringBuilder written, List<ShellCommand> substitutions) {
        position++;
        while (!atEnd() && current() != '"') {
            readExpandedPart(written, substitutions, DOUBLE_QUOTE_ESCAPES);
        }
        position = Math.min(position + 1, text.length());
    }

    private void readExpandedPart(StringBuilder written, List<ShellCommand> substitutions, String escapable) {
        char character = current();
        if (character == '\\' && position + 1 < text.length() && escapable.indexOf(peek(1)) >= 0) {
            position++;
            if (current() != '\n') {
                written.append(current());
            }
            position++;
        } else if (character == '$' && peek(1) == '(' && peek(2) == '(') {
            readArithmetic(written);
        } else if (character == '$' && peek(1) == '(') {
            position++;
            readParenthesisedSubstitution(written, substitutions);
        } else if (character == '`') {
            readBacktickSubstitution(written, substitutions);
        } else {
            written.append(character);
            position++;
        }
    }

    private void readArithmetic(StringBuilder written) {
        int end = text.indexOf("))", position + 3);
        int stop = end < 0 ? text.length() : end + 2;
        written.append(text, position, stop);
        position = stop;
    }

    private void readParenthesisedSubstitution(StringBuilder written, List<ShellCommand> substitutions) {
        ShellParser inner = new ShellParser(text, position + 1, nesting + 1, true);
        inner.parse();
        written.append("$(").append(text, position + 1, inner.position).append(')');
        substitutions.addAll(ShellScript.commandsOf(inner.commands, nesting + 1));
        position = Math.min(inner.position + 1, text.length());
    }

    private void readBacktickSubstitution(StringBuilder written, List<ShellCommand> substitutions) {
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
        written.append('`').append(inner).append('`');
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
}
