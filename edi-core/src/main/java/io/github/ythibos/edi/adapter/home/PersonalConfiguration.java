package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
@RegisterReflectionForBinding({PersonalConfiguration.PersonalDocument.class, PersonalConfiguration.IdentitySection.class,
    PersonalConfiguration.PresenceSection.class})
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PersonalDocument(IdentitySection identity, PresenceSection presence) {

        static final PersonalDocument EMPTY = new PersonalDocument(null, null);

        PersonalDocument {
            identity = Objects.requireNonNullElse(identity, new IdentitySection(null));
            presence = Objects.requireNonNullElse(presence, new PresenceSection(null, null, null));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PresenceSection(String autoQuietAfter, String quietBatch, List<String> calendarIgnore) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IdentitySection(String address) {
    }
}
