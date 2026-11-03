package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.personality.CaptainsPersonality;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.wallpaper.Blink;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
@RegisterReflectionForBinding({PersonalConfiguration.PersonalDocument.class, PersonalConfiguration.IdentitySection.class,
    PersonalConfiguration.PresenceSection.class, PersonalConfiguration.WallpaperSection.class})
public class PersonalConfiguration {

    private final Path file;
    private final YAMLMapper yaml = new YAMLMapper();

    public PersonalConfiguration(EdiHome home) {
        this.file = home.personalConfiguration();
    }

    public CaptainAddress address() {
        try {
            return Optional.ofNullable(document().identity().address()).map(CaptainAddress::new).orElse(CaptainAddress.DEFAULT);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidConfiguration(file, invalid.getMessage());
        }
    }

    public PresenceSettings presence() {
        PresenceSection presence = document().presence();
        try {
            return new PresenceSettings(durationOf(presence.autoQuietAfter(), PresenceSettings.AUTO_QUIET_AFTER),
                    durationOf(presence.quietBatch(), PresenceSettings.QUIET_BATCH), Objects.requireNonNullElse(presence.calendarIgnore(), List.of()));
        } catch (DateTimeParseException invalid) {
            throw new InvalidConfiguration(file, "presence holds \"" + invalid.getParsedString() + "\", which is not a duration such as PT15M.");
        } catch (IllegalArgumentException invalid) {
            throw new InvalidConfiguration(file, invalid.getMessage());
        }
    }

    public Blink blink() {
        try {
            return new Blink(durationOf(document().wallpaper().blinkEvery(), Blink.EVERY));
        } catch (DateTimeParseException invalid) {
            throw new InvalidConfiguration(file, "wallpaper.blinkEvery holds \"" + invalid.getParsedString() + "\", which is not a duration such as PT15S.");
        } catch (IllegalArgumentException invalid) {
            throw new InvalidConfiguration(file, invalid.getMessage());
        }
    }

    public CaptainsPersonality personality() {
        return personalityIn(document());
    }

    public void choose(CaptainsPersonality personality) {
        String section = sectionWriting(personality);
        String chosen = ConfigurationFile.contentOf(file).map(written -> choosingIn(written, section, personality)).orElse(section);
        ConfigurationFile.replace(file, chosen);
    }

    public String contentAddressing(CaptainAddress address) {
        return ConfigurationFile.contentOf(file).orElseGet(() -> addressing(address));
    }

    private PersonalDocument document() {
        try {
            return ConfigurationFile.contentOf(file).map(content -> yaml.readValue(content, PersonalDocument.class)).orElse(PersonalDocument.EMPTY);
        } catch (JacksonException unreadable) {
            throw new InvalidConfiguration(file, unreadable.getOriginalMessage());
        }
    }

    private static Duration durationOf(String written, Duration absent) {
        return written == null ? absent : Duration.parse(written.strip());
    }

    private CaptainsPersonality personalityIn(PersonalDocument document) {
        try {
            return PersonalitySection.personalityIn(document.personality());
        } catch (IllegalArgumentException invalid) {
            throw new InvalidConfiguration(file, invalid.getMessage());
        }
    }

    private String sectionWriting(CaptainsPersonality personality) {
        YAMLMapper writer = ConfigurationYaml.writer();
        ObjectNode configuration = writer.createObjectNode();
        configuration.set(PersonalitySection.NAME, PersonalitySection.sectionOf(personality, writer));
        return writer.writeValueAsString(configuration);
    }

    private String choosingIn(String written, String section, CaptainsPersonality personality) {
        return withTheSectionIn(written, section)
                .filter(edited -> readsAsTheConfigurationChoosing(edited, written, personality))
                .orElseGet(() -> rendered(written, personality));
    }

    private static Optional<String> withTheSectionIn(String written, String section) {
        List<String> lines = new ArrayList<>(written.lines().toList());
        List<Integer> mentions = IntStream.range(0, lines.size()).filter(line -> lines.get(line).startsWith(PersonalitySection.NAME + ":")).boxed().toList();
        if (mentions.isEmpty()) {
            return Optional.of(written + (written.endsWith("\n") ? "" : "\n") + section);
        }
        int start = mentions.getFirst();
        if (mentions.size() > 1 || !lines.get(start).stripTrailing().equals(PersonalitySection.NAME + ":")) {
            return Optional.empty();
        }
        lines.subList(start, endOfTheSection(lines, start)).clear();
        lines.addAll(start, section.lines().toList());
        return Optional.of(String.join("\n", lines) + "\n");
    }

    private static int endOfTheSection(List<String> lines, int start) {
        int end = start + 1;
        while (end < lines.size() && (lines.get(end).isBlank() || Character.isWhitespace(lines.get(end).charAt(0)))) {
            end++;
        }
        while (end > start + 1 && lines.get(end - 1).isBlank()) {
            end--;
        }
        return end;
    }

    private boolean readsAsTheConfigurationChoosing(String edited, String written, CaptainsPersonality personality) {
        try {
            return personalityIn(yaml.readValue(edited, PersonalDocument.class)).equals(personality)
                    && withoutTheSection(edited).equals(withoutTheSection(written));
        } catch (JacksonException | InvalidConfiguration unreadable) {
            return false;
        }
    }

    private JsonNode withoutTheSection(String content) {
        JsonNode configuration = yaml.readTree(content);
        if (configuration instanceof ObjectNode object) {
            object.remove(PersonalitySection.NAME);
        }
        return configuration;
    }

    private String rendered(String written, CaptainsPersonality personality) {
        YAMLMapper writer = ConfigurationYaml.writer();
        ObjectNode configuration = (ObjectNode) yaml.readTree(written);
        configuration.set(PersonalitySection.NAME, PersonalitySection.sectionOf(personality, writer));
        return writer.writeValueAsString(configuration);
    }

    private static String addressing(CaptainAddress address) {
        YAMLMapper writer = ConfigurationYaml.writer();
        ObjectNode configuration = writer.createObjectNode();
        configuration.putObject("identity").put("address", address.word());
        return writer.writeValueAsString(configuration);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PersonalDocument(IdentitySection identity, PresenceSection presence, @JsonProperty(PersonalitySection.NAME) JsonNode personality,
            WallpaperSection wallpaper) {

        static final PersonalDocument EMPTY = new PersonalDocument(null, null, null, null);

        PersonalDocument {
            identity = Objects.requireNonNullElse(identity, new IdentitySection(null));
            presence = Objects.requireNonNullElse(presence, new PresenceSection(null, null, null));
            wallpaper = Objects.requireNonNullElse(wallpaper, new WallpaperSection(null));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PresenceSection(String autoQuietAfter, String quietBatch, List<String> calendarIgnore) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IdentitySection(String address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WallpaperSection(String blinkEvery) {
    }
}
