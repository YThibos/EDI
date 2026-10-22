package io.github.ythibos.edi.web.console;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.presence.PresenceQuery;

@RestController
class SitrepConsoleController {

    private final FleetSnapshotQuery fleet;
    private final SitrepConsolePresenter presenter;
    private final MachineProfile machine;
    private final PersonalConfiguration personal;
    private final PresenceQuery presence;
    private final Clock clock;

    SitrepConsoleController(FleetSnapshotQuery fleet, SitrepConsolePresenter presenter, MachineProfile machine, PersonalConfiguration personal,
            PresenceQuery presence, Clock clock) {
        this.fleet = fleet;
        this.presenter = presenter;
        this.machine = machine;
        this.personal = personal;
        this.presence = presence;
        this.clock = clock;
    }

    @GetMapping("/api/sitrep")
    ConsoleSitrep sitrep() {
        return presenter.present(fleet.snapshot(), clock.now(), clock.zone(), machine.name(), personal.address(), presence.current());
    }
}
