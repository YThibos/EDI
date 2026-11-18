package io.github.ythibos.edi.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.StandInGitHub;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class NpmAuditCheckScriptTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Path SCRIPT = REPOSITORY.resolve("ops/npm-audit/npm-audit-check.mjs");
    private static final Path RECORDED_REPORT = REPOSITORY.resolve("edi-core/src/test/resources/scanner/picomatch-only.json");
    private static final String PICOMATCH = "GHSA-fc5q-39xc-vjpf";
    private static final String LANTERN = "GHSA-2222-3333-4444";
    private static final String TODAY = "2026-11-18";
    private static final String UNPATCHED = "[{\"package\": {\"ecosystem\": \"npm\", \"name\": \"%s\"}, \"first_patched_version\": null}]";
    private static final String PATCHED = "[{\"package\": {\"ecosystem\": \"npm\", \"name\": \"%s\"}, \"first_patched_version\": \"4.0.8\"}]";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    @Test
    void theRecordedReportPassesWithThePicomatchExceptionAndPrintsIt() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(Files.readString(RECORDED_REPORT), exceptions(exception(PICOMATCH, "Build tooling only", "2026-11-18", "2027-02-18")), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output()).contains("Excepted " + PICOMATCH + " (picomatch, high): Build tooling only - reviewed again on 2027-02-18.");
    }

    @Test
    void theExceptedAdvisoryAloneIsEnoughForTheCheckToPass() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high")), picomatchException(), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output()).contains("Excepted " + PICOMATCH, "No high or critical advisory is left unexcepted.");
    }

    @Test
    void aSecondHighAdvisoryFailsTheCheckWhileTheExceptionIsStillPrinted() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high"), advisory("lantern", LANTERN, "high")), picomatchException(), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.output()).contains("Excepted " + PICOMATCH);
        assertThat(run.errors()).contains(LANTERN + " (lantern, high) is not excepted: A flaw in lantern.").doesNotContain("Remove the exception");
    }

    @Test
    void aCriticalAdvisoryAloneFailsTheCheck() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("lantern", LANTERN, "critical")), exceptions(), "");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains(LANTERN + " (lantern, critical) is not excepted");
    }

    @Test
    void aModerateAdvisoryDoesNotFailTheCheck() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("lantern", LANTERN, "moderate")), exceptions(), "");

        assertThat(run.exitCode()).as(run.all()).isZero();
    }

    @Test
    void anExceptionWhoseAdvisoryHasAPatchedVersionFailsAndNamesTheExceptionToRemove() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high")), picomatchException(), patched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("Remove the exception for " + PICOMATCH + ": the advisory has a patched version, 4.0.8.").doesNotContain("is not excepted");
        assertThat(run.output()).doesNotContain("Excepted");
    }

    @Test
    void anExceptionPastItsReviewDateFailsAndNamesTheExceptionToRemove() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high")),
                exceptions(exception(PICOMATCH, "Build tooling only", "2026-08-18", "2026-11-17")), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("Remove the exception for " + PICOMATCH + ": its review date 2026-11-17 has passed.");
    }

    @Test
    void anExceptionStillPassesOnItsReviewDate() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high")),
                exceptions(exception(PICOMATCH, "Build tooling only", "2026-08-18", TODAY)), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).as(run.all()).isZero();
    }

    @Test
    void anExceptionForAnAdvisoryNotInTheTreeFailsAndNamesTheExceptionToRemove() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("lantern", LANTERN, "moderate")), picomatchException(), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("Remove the exception for " + PICOMATCH + ": the advisory no longer applies to the installed tree.");
    }

    @Test
    void anExceptionForAnAdvisoryTheTreeOnlyCarriesBelowTheHighLevelEndsToo() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "moderate")), picomatchException(), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("Remove the exception for " + PICOMATCH + ": the advisory no longer applies to the installed tree.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"advisory", "reason", "accepted", "reviewed"})
    void anExceptionMissingAFieldFailsAndTheAdvisoryStaysUnexcepted(String field) throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high")), exceptions(exceptionWithout(field)), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("Exception 1 lacks its " + field + ".");
        assertThat(run.output()).doesNotContain("Excepted");
    }

    @Test
    void anExceptionByPackageSeverityOrWildcardIsRefused() throws IOException, InterruptedException {
        String byPackage = exceptions("{\"package\": \"picomatch\", \"severity\": \"high\", \"advisory\": \"GHSA-*\", \"reason\": \"x\", \"accepted\": \"2026-11-18\", \"reviewed\": \"2027-02-18\"}");

        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high")), byPackage, unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains(
                "Exception 1 has the field package, which excepts nothing; an exception names one advisory.",
                "Exception 1 has the field severity, which excepts nothing; an exception names one advisory.",
                "Exception 1 names GHSA-*, which is not one GHSA id.");
    }

    @Test
    void anExceptionListedTwiceIsRefused() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high")),
                exceptions(exception(PICOMATCH, "Build tooling only", "2026-11-18", "2027-02-18"), exception(PICOMATCH, "Again", "2026-11-18", "2027-02-18")),
                unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("Remove one of the exceptions for " + PICOMATCH + ": it is listed more than once.");
    }

    @Test
    void aReviewDateNotAfterTheDayOfAcceptanceIsRefused() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(advisory("picomatch", PICOMATCH, "high")),
                exceptions(exception(PICOMATCH, "Build tooling only", "2026-11-18", "2026-11-18")), unpatched(PICOMATCH, "picomatch"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("is reviewed again on 2026-11-18, which is not after the day it was accepted, 2026-11-18.");
    }

    @Test
    void aTreeWithoutHighAdvisoriesPassesWithNoExceptions() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(), exceptions(), "");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output()).doesNotContain("Excepted");
    }

    @Test
    void aFailedAuditIsNotReadAsACleanTree() throws IOException, InterruptedException {
        StandInGitHub.Run run = check("{\"error\": {\"code\": \"ENOTFOUND\", \"summary\": \"request to the registry failed\"}}", exceptions(), "");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("npm audit gave no report: request to the registry failed");
    }

    @Test
    void theExceptionsFileMustHoldOnlyTheExceptionsList() throws IOException, InterruptedException {
        StandInGitHub.Run run = check(report(), "{\"exceptions\": [], \"ignore\": [\"*\"]}", "");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("must hold one object with an \"exceptions\" list and nothing else.");
    }

    @Test
    void anUnknownArgumentIsRefused() throws IOException, InterruptedException {
        StandInGitHub github = serving("");

        StandInGitHub.Run run = github.run(processes, directory, Map.of(), StandInGitHub.node(SCRIPT, "--all", "yes"));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("Usage: npm-audit-check.mjs [--exceptions FILE] [--report FILE] [--today YYYY-MM-DD]");
    }

    private StandInGitHub.Run check(String report, String exceptions, String advisories) throws IOException, InterruptedException {
        Path reportFile = Files.writeString(directory.resolve("audit.json"), report);
        Path exceptionsFile = Files.writeString(directory.resolve("exceptions.json"), exceptions);
        return serving(advisories).run(processes, directory, Map.of(), StandInGitHub.node(SCRIPT,
                "--report", reportFile.toString(), "--exceptions", exceptionsFile.toString(), "--today", TODAY));
    }

    private StandInGitHub serving(String advisories) throws IOException {
        assumeTrue(InstalledPrograms.answers("node", "--version"), "node is not installed");
        return StandInGitHub.serving(directory, "{\"advisories\": {" + advisories + "}}");
    }

    private static String unpatched(String id, String name) {
        return published(id, UNPATCHED.formatted(name));
    }

    private static String patched(String id, String name) {
        return published(id, PATCHED.formatted(name));
    }

    private static String published(String id, String vulnerabilities) {
        return "\"%s\": {\"ghsa_id\": \"%s\", \"vulnerabilities\": %s}".formatted(id, id, vulnerabilities);
    }

    private static String report(String... vulnerabilities) {
        return "{\"auditReportVersion\": 2, \"vulnerabilities\": {" + String.join(", ", vulnerabilities) + "}}";
    }

    private static String advisory(String name, String id, String severity) {
        return """
                "%1$s": {"name": "%1$s", "severity": "%3$s", "via": [{"source": 1, "name": "%1$s", "title": "A flaw in %1$s", "url": "https://github.com/advisories/%2$s", "severity": "%3$s", "range": "<=1.0.0"}]}"""
                .formatted(name, id, severity);
    }

    private static String picomatchException() {
        return exceptions(exception(PICOMATCH, "Build tooling only", "2026-11-18", "2027-02-18"));
    }

    private static String exceptions(String... exceptions) {
        return "{\"exceptions\": [" + String.join(", ", List.of(exceptions)) + "]}";
    }

    private static String exceptionWithout(String field) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("advisory", PICOMATCH);
        fields.put("reason", "Build tooling only");
        fields.put("accepted", "2026-11-18");
        fields.put("reviewed", "2027-02-18");
        fields.remove(field);
        return "{" + String.join(", ", fields.entrySet().stream().map(entry -> "\"%s\": \"%s\"".formatted(entry.getKey(), entry.getValue())).toList()) + "}";
    }

    private static String exception(String advisory, String reason, String accepted, String reviewed) {
        return """
                {
                  "advisory": "%s",
                  "reason": "%s",
                  "accepted": "%s",
                  "reviewed": "%s"
                }""".formatted(advisory, reason, accepted, reviewed);
    }
}
