package io.github.ythibos.edi.application.migration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.event.Actor;

@Service
public class MigrateFirstofficer {

    private final EdiHome home;
    private final ForkMigrationPlanner planner;
    private final BacklogImport backlog;
    private final HomeWriter writer;

    public MigrateFirstofficer(EdiHome home, ForkMigrationPlanner planner, BacklogImport backlog, HomeWriter writer) {
        this.home = home;
        this.planner = planner;
        this.backlog = backlog;
        this.writer = writer;
    }

    public MigrationReport dryRun(Path oldHome) {
        MigrationPlan plan = planner.plan(oldHome);
        return new MigrationReport(plan, backlog.survey(plan), writer.survey(plan.home()), true);
    }

    public MigrationReport run(Path oldHome, boolean overwrite, Actor actor) {
        MigrationReport surveyed = dryRun(oldHome);
        requireAFreshHomeUnless(overwrite, surveyed);
        MigrationPlan plan = surveyed.plan();
        backlog.importInto(BacklogImport.entriesOf(plan), oldHome, actor);
        backlog.continueTheSequences(plan);
        writer.write(plan.home());
        return new MigrationReport(plan, surveyed.backlog(), surveyed.home(), false);
    }

    private void requireAFreshHomeUnless(boolean overwrite, MigrationReport surveyed) {
        if (!overwrite && surveyed.meetsAHomeInUse()) {
            throw new Refusal(home.root() + " already holds " + String.join(" and ", whatTheHomeHolds(surveyed))
                    + "; the dry run lists them, and --overwrite imports beside EDI's own work, keeping each replaced file as <file>"
                    + HomeWriter.KEPT_SUFFIX + ".");
        }
    }

    private static List<String> whatTheHomeHolds(MigrationReport surveyed) {
        List<String> held = new ArrayList<>();
        BacklogSurvey backlog = surveyed.backlog();
        if (backlog.findsWorkOfEdisOwn()) {
            held.add(Counted.things(backlog.ediOwnTasks().size(), "task") + " and " + Counted.things(backlog.ediOwnDecisions().size(), "decision")
                    + " of its own");
        }
        HomeSurvey files = surveyed.home();
        if (files.replacesAnything()) {
            held.add(Counted.things(files.replaced().size() + files.archive().replaced(), "file") + " the import would replace");
        }
        return held;
    }
}
