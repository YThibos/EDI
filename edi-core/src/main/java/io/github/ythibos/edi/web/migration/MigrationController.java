package io.github.ythibos.edi.web.migration;

import java.nio.file.Path;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.migration.ForkMigrationPlanner;
import io.github.ythibos.edi.application.migration.MigrationPlan;

@RestController
class MigrationController {

    private final ForkMigrationPlanner planner;

    MigrationController(ForkMigrationPlanner planner) {
        this.planner = planner;
    }

    @GetMapping("/api/migrations/firstofficer/plan")
    MigrationPlan plan(@RequestParam String from) {
        Path home = Path.of(from);
        if (!home.isAbsolute()) {
            throw new IllegalArgumentException("Name the old home by its absolute path, not " + from + ".");
        }
        return planner.plan(home.normalize());
    }
}
