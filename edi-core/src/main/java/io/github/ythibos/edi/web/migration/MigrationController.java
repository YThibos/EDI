package io.github.ythibos.edi.web.migration;

import java.nio.file.Path;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.migration.MigrateFirstofficer;
import io.github.ythibos.edi.application.migration.MigrationReport;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class MigrationController {

    private static final String COMMAND = "edi migrate firstofficer";

    private final MigrateFirstofficer migration;

    MigrationController(MigrateFirstofficer migration) {
        this.migration = migration;
    }

    @GetMapping("/api/migrations/firstofficer/plan")
    MigrationReport plan(@RequestParam String from, Caller caller) {
        caller.requirePrimary(COMMAND);
        return migration.dryRun(oldHomeNamed(from));
    }

    @PostMapping("/api/migrations/firstofficer")
    MigrationReport migrate(@RequestBody MigrationRequest request, Caller caller) {
        caller.requirePrimary(COMMAND);
        return migration.run(oldHomeNamed(request.from()), request.overwriting(), caller.actor());
    }

    private static Path oldHomeNamed(String from) {
        Path home = Path.of(from == null ? "" : from);
        if (!home.isAbsolute()) {
            throw new IllegalArgumentException("Name the old home by its absolute path, not " + from + ".");
        }
        return home.normalize();
    }
}
