package io.github.ythibos.edi.adapter.tracker;

import java.io.IOException;
import java.nio.file.Files;

import io.github.ythibos.edi.adapter.home.TrackerSettings.Credentials;
import io.github.ythibos.edi.domain.Refusal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

final class KeysFile {

    private static final String TOKEN_FIELD = "API-key";
    private static final YAMLMapper YAML = new YAMLMapper();

    private KeysFile() {
    }

    static String tokenIn(Credentials credentials) {
        JsonNode token = sectionOf(credentials).path(TOKEN_FIELD);
        if (!token.isString() || token.asString().isBlank()) {
            throw new Refusal("The keys file " + credentials.keysFile() + " holds no " + TOKEN_FIELD + " in its " + credentials.section() + " section.");
        }
        return token.asString().strip();
    }

    private static JsonNode sectionOf(Credentials credentials) {
        try {
            return YAML.readTree(Files.readString(credentials.keysFile())).path(credentials.section());
        } catch (IOException unreadable) {
            throw new Refusal("The keys file " + credentials.keysFile() + " cannot be read: " + unreadable.getClass().getSimpleName() + ".");
        } catch (JacksonException malformed) {
            throw new Refusal("The keys file " + credentials.keysFile() + " is not readable YAML.");
        }
    }
}
