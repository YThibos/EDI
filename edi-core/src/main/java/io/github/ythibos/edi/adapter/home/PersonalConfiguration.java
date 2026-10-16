package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
@RegisterReflectionForBinding({PersonalConfiguration.PersonalDocument.class, PersonalConfiguration.IdentitySection.class})
public class PersonalConfiguration {

    private final Path file;
    private final YAMLMapper yaml = new YAMLMapper();

    public PersonalConfiguration(EdiHome home) {
        this.file = home.personalConfiguration();
    }

    public CaptainAddress address() {
        try {
            return ConfigurationFile.contentOf(file)
                    .map(content -> yaml.readValue(content, PersonalDocument.class))
                    .map(PersonalDocument::identity)
                    .map(IdentitySection::address)
                    .map(CaptainAddress::new)
                    .orElse(CaptainAddress.DEFAULT);
        } catch (JacksonException unreadable) {
            throw new InvalidConfiguration(file, unreadable.getOriginalMessage());
        } catch (IllegalArgumentException invalid) {
            throw new InvalidConfiguration(file, invalid.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PersonalDocument(IdentitySection identity) {

        PersonalDocument {
            identity = Objects.requireNonNullElse(identity, new IdentitySection(null));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IdentitySection(String address) {
    }
}
