package io.github.ythibos.edi.application.memory;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.MemoryFiles;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.memory.StartupMemory;
import io.github.ythibos.edi.domain.memory.StowList;

@Service
public class MemoryQuery {

    private final MemoryFiles files;
    private final MachineProfile machine;
    private final Clock clock;

    public MemoryQuery(MemoryFiles files, MachineProfile machine, Clock clock) {
        this.files = files;
        this.machine = machine;
        this.clock = clock;
    }

    public MemoryCheck check() {
        return MemoryCheck.of(startupMemory(), files.directory());
    }

    public StowList stowList() {
        return StowList.of(startupMemory(), LocalDate.ofInstant(clock.now(), ZoneId.systemDefault()));
    }

    public StartupMemory startupMemory() {
        return new StartupMemory(files.loadedAtSessionStart(), machine.startupMemoryBudgetTokens());
    }
}
