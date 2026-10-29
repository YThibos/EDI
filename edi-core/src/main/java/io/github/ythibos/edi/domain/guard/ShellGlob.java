package io.github.ythibos.edi.domain.guard;

import java.util.Map;
import java.util.regex.Pattern;

public final class ShellGlob {

    private static final char ESCAPE = '\\';
    private static final String WILDCARDS = "*?[";
    private static final String EXTENDED_OPENERS = "?*+@!";
    private static final String ANY_RUN = ".*";
    private static final String ANY_ONE = ".";
    private static final Map<String, String> CHARACTER_CLASSES = Map.ofEntries(
            Map.entry("alpha", "\\p{Alpha}"), Map.entry("digit", "\\p{Digit}"), Map.entry("alnum", "\\p{Alnum}"),
            Map.entry("upper", "\\p{Upper}"), Map.entry("lower", "\\p{Lower}"), Map.entry("space", "\\p{Space}"),
            Map.entry("blank", "\\p{Blank}"), Map.entry("punct", "\\p{Punct}"), Map.entry("print", "\\p{Print}"),
            Map.entry("graph", "\\p{Graph}"), Map.entry("cntrl", "\\p{Cntrl}"), Map.entry("xdigit", "\\p{XDigit}"),
            Map.entry("word", "\\w"));

    private ShellGlob() {
    }

    public static boolean isAGlob(String segment) {
        for (int at = 0; at < segment.length(); at++) {
            char character = segment.charAt(at);
            if (character == ESCAPE) {
                at++;
            } else if (WILDCARDS.indexOf(character) >= 0 || groupEnd(segment, at) > 0) {
                return true;
            }
        }
        return false;
    }

    public static Pattern of(String segment) {
        StringBuilder regex = new StringBuilder();
        int at = 0;
        while (at < segment.length()) {
            at = appendNext(segment, at, regex);
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
    }

    public static String literal(String segment) {
        StringBuilder literal = new StringBuilder();
        for (int at = 0; at < segment.length(); at++) {
            char character = segment.charAt(at);
            if (character == ESCAPE && at + 1 < segment.length()) {
                at++;
                character = segment.charAt(at);
            }
            literal.append(character);
        }
        return literal.toString();
    }

    private static int groupEnd(String segment, int at) {
        boolean extended = EXTENDED_OPENERS.indexOf(segment.charAt(at)) >= 0 && at + 1 < segment.length() && segment.charAt(at + 1) == '(';
        if (!extended && segment.charAt(at) != '(') {
            return -1;
        }
        int depth = 0;
        for (int next = extended ? at + 1 : at; next < segment.length(); next++) {
            char character = segment.charAt(next);
            if (character == ESCAPE) {
                next++;
            } else if (character == '(') {
                depth++;
            } else if (character == ')' && --depth == 0) {
                return next + 1;
            }
        }
        return -1;
    }

    private static int appendNext(String segment, int at, StringBuilder regex) {
        char character = segment.charAt(at);
        int group = groupEnd(segment, at);
        if (group > 0) {
            regex.append(ANY_RUN);
            return group;
        }
        if (character == ESCAPE && at + 1 < segment.length()) {
            regex.append(literalOf(segment.codePointAt(at + 1)));
            return at + 1 + Character.charCount(segment.codePointAt(at + 1));
        }
        if (character == '*' || character == '?') {
            regex.append(character == '*' ? ANY_RUN : ANY_ONE);
            return at + 1;
        }
        int bracketEnd = character == '[' ? bracketEnd(segment, at) : -1;
        if (bracketEnd > 0) {
            regex.append(bracketOf(segment.substring(at + 1, bracketEnd)));
            return bracketEnd + 1;
        }
        regex.append(literalOf(segment.codePointAt(at)));
        return at + Character.charCount(segment.codePointAt(at));
    }

    private static String literalOf(int codePoint) {
        return Character.isLetterOrDigit(codePoint) ? Character.toString(codePoint) : "\\x{" + Integer.toHexString(codePoint) + "}";
    }

    private static int bracketEnd(String segment, int open) {
        int at = open + 1;
        if (at < segment.length() && (segment.charAt(at) == '!' || segment.charAt(at) == '^')) {
            at++;
        }
        if (at < segment.length() && segment.charAt(at) == ']') {
            at++;
        }
        while (at < segment.length()) {
            char character = segment.charAt(at);
            int nameEnd = character == '[' ? namedMemberEnd(segment, at) : -1;
            if (nameEnd > 0) {
                at = nameEnd;
            } else if (character == ']') {
                return at;
            } else {
                at += character == ESCAPE ? 2 : 1;
            }
        }
        return -1;
    }

    private static int namedMemberEnd(String segment, int open) {
        if (open + 1 >= segment.length() || ":=.".indexOf(segment.charAt(open + 1)) < 0) {
            return -1;
        }
        int close = segment.indexOf(segment.charAt(open + 1) + "]", open + 2);
        return close < 0 ? -1 : close + 2;
    }

    private static String bracketOf(String members) {
        boolean negated = !members.isEmpty() && (members.charAt(0) == '!' || members.charAt(0) == '^');
        StringBuilder set = new StringBuilder();
        int at = negated ? 1 : 0;
        while (at < members.length()) {
            int nameEnd = members.charAt(at) == '[' ? namedMemberEnd(members, at) : -1;
            if (nameEnd > 0) {
                String named = namedClassOf(members.substring(at, nameEnd));
                if (named == null) {
                    return ANY_ONE;
                }
                set.append(named);
                at = nameEnd;
            } else {
                at = appendMember(members, at, set);
            }
        }
        if (set.isEmpty()) {
            return negated ? ANY_ONE : "(?!)";
        }
        return "[" + (negated ? "^" : "") + set + "]";
    }

    private static String namedClassOf(String member) {
        return member.startsWith("[:") ? CHARACTER_CLASSES.get(member.substring(2, member.length() - 2)) : null;
    }

    private static int appendMember(String members, int at, StringBuilder set) {
        int first = memberAt(members, at);
        int afterFirst = at + memberLength(members, at);
        if (afterFirst + 1 < members.length() && members.charAt(afterFirst) == '-') {
            int last = memberAt(members, afterFirst + 1);
            if (first <= last) {
                set.append(literalOf(first)).append('-').append(literalOf(last));
            }
            return afterFirst + 1 + memberLength(members, afterFirst + 1);
        }
        set.append(literalOf(first));
        return afterFirst;
    }

    private static int memberAt(String members, int at) {
        return members.charAt(at) == ESCAPE && at + 1 < members.length() ? members.codePointAt(at + 1) : members.codePointAt(at);
    }

    private static int memberLength(String members, int at) {
        return members.charAt(at) == ESCAPE && at + 1 < members.length()
                ? 1 + Character.charCount(members.codePointAt(at + 1))
                : Character.charCount(members.codePointAt(at));
    }
}
