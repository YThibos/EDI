package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WindowsStandIn {

    private static final String STAND_IN = """
            #!/bin/sh
            place='%s'
            for argument; do last="$argument"; done
            printf '%%s\\n' "$last" >> "$place/calls.log"
            printf '%%s\\n' "${WSL_INTEROP:-}" >> "$place/interop.log"
            if [ -f "$place/calendar.failure" ] && printf '%%s' "$last" | base64 -d | tr -d '\\000' | grep -q GetDefaultFolder; then
                cat "$place/calendar.failure" >&2
                exit 1
            fi
            if [ -f "$place/calendar.json" ]; then
                cat "$place/calendar.json"
            fi
            """;
    private static final Pattern ENCODED_TEXT = Pattern.compile("FromBase64String\\('([A-Za-z0-9+/=]*)'\\)");
    private static final String TOAST_SCRIPT_MARK = "ToastNotificationManager";
    private static final String CALENDAR_SCRIPT_MARK = "GetDefaultFolder";

    private final Path place;

    private WindowsStandIn(Path place) {
        this.place = place;
    }

    public static WindowsStandIn in(Path directory) throws IOException {
        WindowsStandIn standIn = new WindowsStandIn(Files.createDirectories(directory));
        StandInScripts.installed(directory.resolve("powershell.exe"), STAND_IN.formatted(directory));
        return standIn;
    }

    public WindowsStandIn showingTheCalendar(String exported) throws IOException {
        Files.deleteIfExists(place.resolve("calendar.failure"));
        Files.writeString(place.resolve("calendar.json"), exported);
        return this;
    }

    public WindowsStandIn failingToReadTheCalendar(String reason) throws IOException {
        Files.writeString(place.resolve("calendar.failure"), reason);
        return this;
    }

    public String executable() {
        return place.resolve("powershell.exe").toString();
    }

    public Path windowsDirectory() {
        return place;
    }

    public List<String> toasts() throws IOException {
        return scripts().stream().filter(script -> script.contains(TOAST_SCRIPT_MARK)).map(WindowsStandIn::toastOf).toList();
    }

    public long calendarReads() throws IOException {
        return scripts().stream().filter(script -> script.contains(CALENDAR_SCRIPT_MARK)).count();
    }

    public List<String> interopSockets() throws IOException {
        return linesOf("interop.log");
    }

    private List<String> scripts() throws IOException {
        return linesOf("calls.log").stream()
                .map(encoded -> new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_16LE))
                .toList();
    }

    private List<String> linesOf(String file) throws IOException {
        Path log = place.resolve(file);
        return Files.exists(log) ? Files.readAllLines(log) : List.of();
    }

    private static String toastOf(String script) {
        Matcher texts = ENCODED_TEXT.matcher(script);
        StringBuilder toast = new StringBuilder();
        while (texts.find()) {
            toast.append(toast.isEmpty() ? "" : " | ").append(new String(Base64.getDecoder().decode(texts.group(1)), StandardCharsets.UTF_8));
        }
        return toast.toString();
    }
}
