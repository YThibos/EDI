package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

final class RuleFirings {

    static final String SPOOL = "rules-fired.jsonl";
    static final String REFUSED = "refused";
    static final String WARNED = "warned";
    static final String ALLOWED = "allowed";

    private static final String FIRED = "/api/rules/fired";
    private static final String PRIMARY = "primary";

    private final Edi edi;
    private final boolean coreMayAnswer;

    private RuleFirings(Edi edi, boolean coreMayAnswer) {
        this.edi = edi;
        this.coreMayAnswer = coreMayAnswer;
    }

    static RuleFirings throughTheCore(Edi edi) {
        return new RuleFirings(edi, true);
    }

    static RuleFirings intoTheSpool(Edi edi) {
        return new RuleFirings(edi, false);
    }

    static Path spoolIn(Path home) {
        return home.resolve("state").resolve(SPOOL);
    }

    void fired(String rule, String outcome, String reason) {
        JsonBody firing = new JsonBody().with("rule", rule).with("outcome", outcome).with("reason", reason);
        if (!coreMayAnswer || !toldTheCore(firing)) {
            spool(firing);
        }
    }

    private boolean toldTheCore(JsonBody firing) {
        try {
            return new CoreClient().post(edi.core(), FIRED, firing.text(), "text/plain", CoreClient.PATIENCE).succeeded();
        } catch (CoreUnavailable unavailable) {
            return false;
        }
    }

    private void spool(JsonBody firing) {
        Map<String, String> environment = edi.environment();
        String line = firing.with("at", Instant.now().toString())
                .with("actor", actorOf(environment))
                .with("task", environment.get("EDI_TASK_ID"))
                .with("session", edi.claudeSession().idIfAny().orElse(null))
                .text();
        try {
            Path spool = spoolIn(edi.home());
            Files.createDirectories(spool.getParent());
            Files.writeString(spool, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException _) {
        }
    }

    private static String actorOf(Map<String, String> environment) {
        String worker = environment.get("EDI_WORKER");
        return worker == null || worker.isBlank() ? PRIMARY : "worker:" + worker;
    }
}
