package io.github.ythibos.edi.domain.guard;

import java.util.regex.Pattern;

record GraphQlDocument(String text) {

    private static final Pattern MUTATION = Pattern.compile("(?<![\\w$])mutation(?!\\w)");
    private static final Pattern MERGING_MUTATION = Pattern.compile(
            "(?<![\\w$])(mergePullRequest|enablePullRequestAutoMerge|enqueuePullRequest|mergeBranch|mergeRequestAccept)(?!\\w)",
            Pattern.CASE_INSENSITIVE);
    private static final String BLOCK_QUOTE = "\"\"\"";
    private static final String ESCAPED_BLOCK_QUOTE = "\\\"\"\"";
    private static final char QUOTE = '"';
    private static final char ESCAPE = '\\';
    private static final char COMMENT = '#';
    private static final String STRING_LEFT_OUT = " ";

    boolean mergesOrArmsAMerge() {
        String read = withoutStrings();
        return MUTATION.matcher(read).find() && MERGING_MUTATION.matcher(read).find();
    }

    private String withoutStrings() {
        StringBuilder read = new StringBuilder();
        int position = 0;
        while (position < text.length()) {
            position = readTokenAt(position, read);
        }
        return read.toString();
    }

    private int readTokenAt(int position, StringBuilder read) {
        if (text.startsWith(BLOCK_QUOTE, position)) {
            read.append(STRING_LEFT_OUT);
            return blockStringEndFrom(position + BLOCK_QUOTE.length());
        }
        if (text.charAt(position) == QUOTE) {
            read.append(STRING_LEFT_OUT);
            return stringEndFrom(position + 1);
        }
        int end = text.charAt(position) == COMMENT ? lineEndFrom(position) : position + 1;
        read.append(text, position, end);
        return end;
    }

    private int blockStringEndFrom(int start) {
        int position = start;
        while (position < text.length() && !text.startsWith(BLOCK_QUOTE, position)) {
            position += text.startsWith(ESCAPED_BLOCK_QUOTE, position) ? ESCAPED_BLOCK_QUOTE.length() : 1;
        }
        return Math.min(position + BLOCK_QUOTE.length(), text.length());
    }

    private int stringEndFrom(int start) {
        int position = start;
        while (position < text.length() && text.charAt(position) != QUOTE && !endsALine(text.charAt(position))) {
            position += escapesTheNextCharacter(position) ? 2 : 1;
        }
        return position < text.length() && text.charAt(position) == QUOTE ? position + 1 : position;
    }

    private int lineEndFrom(int start) {
        int position = start;
        while (position < text.length() && !endsALine(text.charAt(position))) {
            position++;
        }
        return position;
    }

    private static boolean endsALine(char character) {
        return character == '\n' || character == '\r';
    }

    private boolean escapesTheNextCharacter(int position) {
        return text.charAt(position) == ESCAPE && position + 1 < text.length() && !endsALine(text.charAt(position + 1));
    }
}
