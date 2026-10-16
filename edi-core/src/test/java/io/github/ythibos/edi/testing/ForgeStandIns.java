package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

public final class ForgeStandIns {

    public static final String TOKEN = "synthetic-forge-token";

    private static final String STAND_IN = """
            #!/bin/sh
            place='%s'
            printf '%%s\\n' "$*" >> "$place/calls.log"
            printf '%%s\\n' "${GH_TOKEN:-none}" >> "$place/tokens.log"
            if [ "$1 $2" = "pr create" ]; then
                cat > "$place/body.txt"
            fi
            answer="$place/answers/$1-$2"
            if [ -f "$answer" ]; then
                cat "$answer"
            fi
            """;

    private final Path place;

    private ForgeStandIns(Path place) {
        this.place = place;
    }

    public static ForgeStandIns in(Path directory) throws IOException {
        ForgeStandIns standIns = new ForgeStandIns(Files.createDirectories(directory));
        Files.createDirectories(directory.resolve("answers"));
        standIns.install("gh");
        standIns.install("glab");
        return standIns.answering("auth-token", TOKEN + "\n");
    }

    public ForgeStandIns answering(String command, String answer) throws IOException {
        Files.writeString(place.resolve("answers").resolve(command), answer);
        return this;
    }

    public String gh() {
        return place.resolve("gh").toString();
    }

    public String glab() {
        return place.resolve("glab").toString();
    }

    public List<String> calls() throws IOException {
        Path log = place.resolve("calls.log");
        return Files.exists(log) ? Files.readAllLines(log) : List.of();
    }

    public List<String> tokens() throws IOException {
        Path log = place.resolve("tokens.log");
        return Files.exists(log) ? Files.readAllLines(log) : List.of();
    }

    public String body() throws IOException {
        return Files.readString(place.resolve("body.txt"));
    }

    private void install(String tool) throws IOException {
        Path script = place.resolve(tool);
        Files.writeString(script, STAND_IN.formatted(place));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
    }
}
