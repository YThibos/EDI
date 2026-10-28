package io.github.ythibos.edi.application.memory;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.MemoryFiles;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.memory.StartupMemory;
import io.github.ythibos.edi.domain.memory.StowList;
import io.github.ythibos.edi.domain.memory.WorkaroundCount;

@Service
public class MemoryQuery {

    private final MemoryFiles files;
    private final MachineProfile machine;
    private final WorkaroundTickets tickets;
    private final Clock clock;

    public MemoryQuery(MemoryFiles files, MachineProfile machine, WorkaroundTickets tickets, Clock clock) {
        this.files = files;
        this.machine = machine;
        this.tickets = tickets;
        this.clock = clock;
    }

    public MemoryCheck check() {
        return MemoryCheck.of(startupMemory(), files.directory());
    }

    public StowList stowList() {
        StartupMemory memory = startupMemory();
        return StowList.of(memory, LocalDate.ofInstant(clock.now(), clock.zone()), tickets.fromTheStoreAndTheTrackers(memory.workaroundKeys()));
    }

    public WorkaroundCount workarounds() {
        StartupMemory memory = startupMemory();
        return WorkaroundCount.of(memory, tickets.fromTheStore(memory.workaroundKeys()));
    }

    public StartupMemory startupMemory() {
        return new StartupMemory(files.loadedAtSessionStart(), machine.startupMemoryBudgetTokens());
    }
}
