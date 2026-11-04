package io.github.ythibos.edi.testing;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.windows.OutlookCalendar;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.presence.CalendarEntry;
import tools.jackson.databind.json.JsonMapper;

public final class RecordingCalendar extends OutlookCalendar {

    private static final String NO_POWERSHELL = "";
    private static final Path NO_WINDOWS = Path.of("/nonexistent/windows");

    private final MachineProfile machine;
    private final List<LocalDate> readDays = new CopyOnWriteArrayList<>();

    public RecordingCalendar(ExternalPrograms programs, MachineProfile machine, JsonMapper json, Clock clock) {
        super(programs, machine, json, NO_POWERSHELL, NO_WINDOWS, clock);
        this.machine = machine;
    }

    @Override
    public boolean isConfigured() {
        return machine.windowsSide().outlookCalendar();
    }

    @Override
    public List<CalendarEntry> entriesOn(LocalDate day) {
        readDays.add(day);
        return List.of();
    }

    public List<LocalDate> readDays() {
        return List.copyOf(readDays);
    }
}
