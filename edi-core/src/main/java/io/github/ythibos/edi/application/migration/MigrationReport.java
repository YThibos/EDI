package io.github.ythibos.edi.application.migration;

public record MigrationReport(MigrationPlan plan, BacklogSurvey backlog, HomeSurvey home, boolean dryRun) {

    public boolean meetsAHomeInUse() {
        return backlog.findsWorkOfEdisOwn() || home.replacesAnything();
    }
}
