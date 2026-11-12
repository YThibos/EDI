package io.github.ythibos.edi.application.rule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
        Path takingIn = spool.resolveSibling(spool.getFileName() + TAKING_IN);
        recordedAndDeleted(takingIn);
        if (Files.exists(spool)) {
            moved(spool, takingIn);
            recordedAndDeleted(takingIn);
        }
    }

    private void recordedAndDeleted(Path takingIn) {
        if (Files.exists(takingIn)) {
            List<SpooledFiring> spooled = linesOf(takingIn).flatMap(line -> firingOn(line).stream()).toList();
            transaction.executeWithoutResult(_ -> spooled.forEach(firing -> firings.record(firing.fired(), firing.at())));
            deleted(takingIn);
        }
    }

    private static Stream<String> linesOf(Path takingIn) {
        try {
            return new String(Files.readAllBytes(takingIn), StandardCharsets.ISO_8859_1).lines().flatMap(line -> decoded(line).stream());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Optional<String> decoded(String undecodedLine) {
        ByteBuffer bytes = ByteBuffer.wrap(undecodedLine.getBytes(StandardCharsets.ISO_8859_1));
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder().decode(bytes).toString());
        } catch (CharacterCodingException undecodable) {
            LOG.warn("A rule firing spooled while the core was out of reach is not UTF-8, so it is left out.");
            return Optional.empty();
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

    private static void deleted(Path takingIn) {
        try {
            Files.delete(takingIn);
        } catch (IOException undeletable) {
            throw new UncheckedIOException(undeletable);
        }
    }

    private static void moved(Path spool, Path takingIn) {
        try {
            Files.move(spool, takingIn, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unmovable) {
            throw new UncheckedIOException(unmovable);
        }
    }

    private record SpooledFiring(RuleFired fired, Instant at) {
    }
}
