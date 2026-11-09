package io.github.ythibos.edi.application.rule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.store.RuleFirings;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleFired;
import io.github.ythibos.edi.domain.rule.RuleOutcome;
import io.github.ythibos.edi.domain.rule.RuleReason;
import io.github.ythibos.edi.domain.task.TaskId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TakeInSpooledFirings {

    private static final Logger LOG = LoggerFactory.getLogger(TakeInSpooledFirings.class);
    private static final String TAKING_IN = ".taking-in";

    private final EdiHome home;
    private final RuleFirings firings;
    private final JsonMapper json;
    private final TransactionTemplate transaction;

    public TakeInSpooledFirings(EdiHome home, RuleFirings firings, JsonMapper json, TransactionTemplate transaction) {
        this.home = home;
        this.firings = firings;
        this.json = json;
        this.transaction = transaction;
    }

    public void takeIn() {
        Path spool = home.spooledRuleFirings();
        Path takenIn = spool.resolveSibling(spool.getFileName() + TAKING_IN);
        if (Files.exists(spool)) {
            moved(spool, takenIn);
        }
        if (Files.exists(takenIn)) {
            List<SpooledFiring> spooled = linesOf(takenIn).stream().flatMap(line -> firingOn(line).stream()).toList();
            transaction.executeWithoutResult(_ -> spooled.forEach(firing -> firings.record(firing.fired(), firing.at())));
            deleted(takenIn);
        }
    }

    private static void moved(Path spool, Path takenIn) {
        try {
            Files.move(spool, takenIn, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unmovable) {
            throw new UncheckedIOException(unmovable);
        }
    }

    private static List<String> linesOf(Path takenIn) {
        try {
            return Files.readAllLines(takenIn);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private Optional<SpooledFiring> firingOn(String line) {
        try {
            JsonNode firing = json.readTree(line);
            return Optional.of(new SpooledFiring(new RuleFired(ruleOf(firing), outcomeOf(firing), new Actor(firing.path("actor").asString()),
                    taskOf(firing), sessionOf(firing), RuleReason.of(firing.path("reason").asString())), Instant.parse(firing.path("at").asString())));
        } catch (JacksonException | IllegalArgumentException | DateTimeException unreadable) {
            LOG.warn("A rule firing spooled while the core was out of reach cannot be read, so it is left out: {}", unreadable.getMessage());
            return Optional.empty();
        }
    }

    private static Rule ruleOf(JsonNode firing) {
        String id = firing.path("rule").asString();
        return Rule.withId(id).orElseThrow(() -> new IllegalArgumentException("No rule of the catalogue has the id " + id + "."));
    }

    private static RuleOutcome outcomeOf(JsonNode firing) {
        String outcome = firing.path("outcome").asString();
        return RuleOutcome.named(outcome).orElseThrow(() -> new IllegalArgumentException("No rule fires as " + outcome + "."));
    }

    private static TaskId taskOf(JsonNode firing) {
        String task = firing.path("task").asString(null);
        return task == null || !TaskId.isWritten(task) ? null : new TaskId(task);
    }

    private static UUID sessionOf(JsonNode firing) {
        String session = firing.path("session").asString(null);
        return session == null ? null : UUID.fromString(session);
    }

    private static void deleted(Path takenIn) {
        try {
            Files.delete(takenIn);
        } catch (IOException undeletable) {
            throw new UncheckedIOException(undeletable);
        }
    }

    private record SpooledFiring(RuleFired fired, Instant at) {
    }
}
