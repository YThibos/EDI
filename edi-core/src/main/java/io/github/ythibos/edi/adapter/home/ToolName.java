package io.github.ythibos.edi.adapter.home;

final class ToolName {

    private ToolName() {
    }

    static boolean isWellFormed(String tool) {
        if (tool == null || tool.isEmpty() || tool.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        int open = tool.indexOf('(');
        String name = open < 0 ? tool : tool.substring(0, open);
        return isIdentifier(name) && (open < 0 || hasOnePatternAtTheEnd(tool, open));
    }

    private static boolean isIdentifier(String name) {
        return !name.isEmpty() && Character.isLetter(name.charAt(0))
                && name.chars().allMatch(character -> Character.isLetterOrDigit(character) || character == '_');
    }

    private static boolean hasOnePatternAtTheEnd(String tool, int open) {
        String pattern = tool.substring(open + 1);
        return pattern.length() > 1 && pattern.endsWith(")") && pattern.indexOf('(') < 0 && pattern.indexOf(')') == pattern.length() - 1;
    }
}
