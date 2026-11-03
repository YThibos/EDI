package io.github.ythibos.edi.domain.delivery;

import io.github.ythibos.edi.domain.AsciiCharacters;

final class HostNames {

    private static final int LONGEST_NAME = 253;
    private static final int LONGEST_LABEL = 63;
    private static final int LONGEST_PORT = 5;
    private static final int HIGHEST_PORT = 65535;
    private static final String LABEL_SEPARATOR = "\\.";
    private static final char PORT_MARK = ':';
    private static final char HYPHEN = '-';

    private HostNames() {
    }

    static boolean isDnsName(String host) {
        return host != null
                && !host.isEmpty()
                && host.length() <= LONGEST_NAME
                && allLabels(host.split(LABEL_SEPARATOR, -1));
    }

    static boolean isDnsNameWithAnyPort(String host) {
        if (host == null) {
            return false;
        }
        int port = host.indexOf(PORT_MARK);
        return port < 0 ? isDnsName(host) : isDnsName(host.substring(0, port)) && isPort(host.substring(port + 1));
    }

    private static boolean allLabels(String[] labels) {
        for (String label : labels) {
            if (!isLabel(label)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLabel(String label) {
        return !label.isEmpty()
                && label.length() <= LONGEST_LABEL
                && label.charAt(0) != HYPHEN
                && label.charAt(label.length() - 1) != HYPHEN
                && label.chars().allMatch(character -> AsciiCharacters.isLetterOrDigit(character) || character == HYPHEN);
    }

    private static boolean isPort(String port) {
        return !port.isEmpty() && port.length() <= LONGEST_PORT && AsciiCharacters.isDigits(port) && Integer.parseInt(port) >= 1
                && Integer.parseInt(port) <= HIGHEST_PORT;
    }
}
