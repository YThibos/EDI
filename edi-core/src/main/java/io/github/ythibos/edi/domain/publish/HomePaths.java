package io.github.ythibos.edi.domain.publish;

import java.util.regex.Pattern;

final class HomePaths {

    private static final Pattern HOME_PATH = Pattern.compile(
            "(?<![\\w.])(?:/home/[^/\\s]+|/Users/[^/\\s]+|/root(?=/|\\b)|/mnt/[a-z]/Users/[^/\\s]+|[A-Za-z]:\\\\Users\\\\[^\\\\\\s]+)");

    private HomePaths() {
    }

    static boolean appearIn(String text) {
        return HOME_PATH.matcher(text).find();
    }
}
