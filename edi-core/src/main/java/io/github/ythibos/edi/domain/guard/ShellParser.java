package io.github.ythibos.edi.domain.guard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class ShellParser {

    private static final String COMMAND_SEPARATORS = ";&|()";
    private static final String REDIRECTION_CHARACTERS = "<>&|";
    private static final String WORD_BREAKS = " \t\n;&|()<>";

    private final String text;
    private final int nesting;
    private final List<List<ShellWord>> commands = new ArrayList<>();
    private final Deque<String> heredocDelimiters = new ArrayDeque<>();
    private List<ShellWord> words = new ArrayList<>();
    private int position;

    private ShellParser(String text, int nesting) {
        this.text = text;
        this.nesting = nesting;
    }

    static List<List<ShellWord>> simpleCommandsIn(String text, int nesting) {
        ShellParser parser = new ShellParser(text, nesting);
        parser.parse();
        return parser.commands;
    }

    private void parse() {
        while (!atEnd()) {
            char character = current();
            if (character == ' ' || character == '\t') {
                position++;
            } else if (character == '\n') {
                endCommand();
                position++;
                skipHeredocBodies();
            } else if (character == '#') {
                skipComment();
            } else if (startsRedirection()) {
                readRedirection();
            } else if (COMMAND_SEPARATORS.indexOf(character) >= 0) {
                endCommand();
                position++;
            } else {
                readWord();
            }
        }
        endCommand();
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
        } else if (text.startsWith("<<", position)) {
            position += 2;
            readHeredocDelimiter();
        } else {
            position++;
            while (!atEnd() && REDIRECTION_CHARACTERS.indexOf(current()) >= 0) {
                position++;
            }
        }
    }

    private ShellWord processSubstitution() {
        StringBuilder written = new StringBuilder();
        List<ShellCommand> substitutions = new ArrayList<>();
        readParenthesisedSubstitution(written, substitutions, 1);
        return new ShellWord(written.toString(), substitutions);
    }

    private void readHeredocDelimiter() {
        if (!atEnd() && current() == '-') {
            position++;
        }
        skipBlanks();
        StringBuilder delimiter = new StringBuilder();
        while (!atEnd() && WORD_BREAKS.indexOf(current()) < 0) {
            char character = current();
            if (character != '\'' && character != '"' && character != '\\') {
                delimiter.append(character);
            }
            position++;
        }
        heredocDelimiters.add(delimiter.toString());
    }

    private void skipHeredocBodies() {
        while (!heredocDelimiters.isEmpty() && !atEnd()) {
            String delimiter = heredocDelimiters.peek();
            int lineEnd = lineEndFrom(position);
            if (text.substring(position, lineEnd).strip().equals(delimiter)) {
                heredocDelimiters.remove();
            }
            position = Math.min(lineEnd + 1, text.length());
        }
    }

    private void skipComment() {
        position = lineEndFrom(position);
    }

    private void readWord() {
        StringBuilder written = new StringBuilder();
        List<ShellCommand> substitutions = new ArrayList<>();
        while (!atEnd() && WORD_BREAKS.indexOf(current()) < 0) {
            readWordPart(written, substitutions);
        }
        words.add(new ShellWord(written.toString(), substitutions));
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
            readParenthesisedSubstitution(written, substitutions, 1);
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
            char character = current();
            if (character == '\\' && position + 1 < text.length() && "\"\\$`\n".indexOf(peek(1)) >= 0) {
                position++;
                if (current() != '\n') {
                    written.append(current());
                }
                position++;
            } else if (character == '$' && peek(1) == '(' && peek(2) == '(') {
                readArithmetic(written);
            } else if (character == '$' && peek(1) == '(') {
                position++;
                readParenthesisedSubstitution(written, substitutions, 1);
            } else if (character == '`') {
                readBacktickSubstitution(written, substitutions);
            } else {
                written.append(character);
                position++;
            }
        }
        position = Math.min(position + 1, text.length());
    }

    private void readArithmetic(StringBuilder written) {
        int end = text.indexOf("))", position + 3);
        int stop = end < 0 ? text.length() : end + 2;
        written.append(text, position, stop);
        position = stop;
    }

    private void readParenthesisedSubstitution(StringBuilder written, List<ShellCommand> substitutions, int depth) {
        int start = position + 1;
        int cursor = start;
        int open = depth;
        while (cursor < text.length() && open > 0) {
            char character = text.charAt(cursor);
            if (character == '\\') {
                cursor += 2;
                continue;
            }
            if (character == '\'') {
                int closing = text.indexOf('\'', cursor + 1);
                cursor = closing < 0 ? text.length() : closing + 1;
                continue;
            }
            if (character == '(') {
                open++;
            } else if (character == ')') {
                open--;
            }
            cursor++;
        }
        int innerEnd = open == 0 ? cursor - 1 : text.length();
        String inner = text.substring(start, Math.min(innerEnd, text.length()));
        written.append("$(").append(inner).append(')');
        substitutions.addAll(ShellScript.commandsIn(inner, nesting + 1));
        position = Math.min(cursor, text.length());
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
}
