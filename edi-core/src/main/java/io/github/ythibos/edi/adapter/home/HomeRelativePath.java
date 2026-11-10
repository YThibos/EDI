package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;

public final class HomeRelativePath {

    private static final String HOME_MARK = "~/";

    private HomeRelativePath() {
    }

    public static Path expanded(String path) {
        return path.startsWith(HOME_MARK) ? Path.of(System.getProperty("user.home")).resolve(path.substring(HOME_MARK.length())) : Path.of(path);
    }
}
