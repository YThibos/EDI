package io.github.ythibos.edi.adapter.windows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.domain.presence.CalendarEntry;
import io.github.ythibos.edi.domain.presence.CalendarEntry.Response;
import io.github.ythibos.edi.domain.presence.CalendarEntry.Showing;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ImportRuntimeHints(WindowsScripts.class)
public class OutlookCalendar {

    private static final Map<String, String> WSL_WIDE_INTEROP = Map.of("WSL_INTEROP", "/run/WSL/1_interop");

    private final ExternalPrograms programs;
    private final MachineProfile machine;
    private final JsonMapper json;
    private final String executable;
    private final Path windowsDirectory;

    public OutlookCalendar(ExternalPrograms programs, MachineProfile machine, JsonMapper json, @Value("${edi.windows.powershell:}") String executable,
            @Value("${edi.console.windows-directory:/mnt/c}") Path windowsDirectory) {
        this.programs = programs;
        this.machine = machine;
        this.json = json;
        this.executable = executable;
        this.windowsDirectory = windowsDirectory;
    }

    public boolean isConfigured() {
        return machine.windowsSide().outlookCalendar() && Files.isDirectory(windowsDirectory);
    }

    public List<CalendarEntry> entriesOn(LocalDate day) {
        String script = WindowsScripts.read(WindowsScripts.OUTLOOK_CALENDAR).replace("__DAY__", day.toString());
        return entriesIn(programs.run(withTheCaptainsOrdinaryRights(ProgramCall.of(Program.POWERSHELL, executable, windowsDirectory,
                WindowsScripts.encodedCommand(script)))).outputOfSuccess());
    }

    private static ProgramCall withTheCaptainsOrdinaryRights(ProgramCall call) {
        return call.withVariables(WSL_WIDE_INTEROP);
    }

    private List<CalendarEntry> entriesIn(String output) {
        if (output.isBlank()) {
            return List.of();
        }
        try {
            JsonNode exported = json.readTree(output.strip());
            Iterable<JsonNode> items = exported.isArray() ? exported : List.of(exported);
            return StreamSupport.stream(items.spliterator(), false).map(OutlookCalendar::entry).toList();
        } catch (JacksonException | DateTimeParseException unreadable) {
            throw new ProgramFailure("The Outlook calendar's export is not readable: " + unreadable.getMessage(), unreadable);
        }
    }

    private static CalendarEntry entry(JsonNode item) {
        return new CalendarEntry(item.path("Subject").asString(""), instant(item.path("Start").asString("")), instant(item.path("End").asString("")),
                item.path("AllDay").asBoolean(false), responseOf(item.path("ResponseStatus").asInt(0)), showingOf(item.path("BusyStatus").asInt(2)));
    }

    private static Instant instant(String written) {
        try {
            return OffsetDateTime.parse(written).toInstant();
        } catch (DateTimeParseException withoutAnOffset) {
            return LocalDateTime.parse(written).atZone(ZoneId.systemDefault()).toInstant();
        }
    }

    private static Response responseOf(int status) {
        return status >= 0 && status < Response.values().length ? Response.values()[status] : Response.NOT_RESPONDED;
    }

    private static Showing showingOf(int status) {
        return status >= 0 && status < Showing.values().length ? Showing.values()[status] : Showing.BUSY;
    }
}
