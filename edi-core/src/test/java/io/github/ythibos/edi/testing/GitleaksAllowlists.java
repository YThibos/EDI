package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import tools.jackson.dataformat.toml.TomlMapper;

final class GitleaksAllowlists {

    private static final String DEFAULT_CONFIGURATION_EXCERPT = "gitleaks/default-allowlists.toml";

    private final String version;
    private final Allowlist global;

    private GitleaksAllowlists(DefaultConfiguration configuration) {
        this.version = configuration.version();
        this.global = configuration.allowlist();
    }

    static GitleaksAllowlists ofTheDefaultConfiguration() {
        try (InputStream excerpt = GitleaksAllowlists.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIGURATION_EXCERPT)) {
            return new GitleaksAllowlists(new TomlMapper().readValue(excerpt, DefaultConfiguration.class));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    String version() {
        return version;
    }

    boolean allow(String secret) {
        return global.allows(secret);
    }

    private record DefaultConfiguration(String version, Allowlist allowlist) {
    }

    private record Allowlist(List<Pattern> regexes, List<String> stopwords) {

        private static final Pattern GO_LITERAL_OPENING_BRACE = Pattern.compile("(?<!\\\\)\\{(?!\\d+(?:,\\d*)?})");

        @JsonCreator
        static Allowlist of(
                @JsonProperty("regexes") @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> regexes,
                @JsonProperty("stopwords") @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> stopwords) {
            return new Allowlist(regexes.stream().map(Allowlist::inJavaSyntax).toList(), stopwords);
        }

        private static Pattern inJavaSyntax(String goRegex) {
            return Pattern.compile(GO_LITERAL_OPENING_BRACE.matcher(goRegex).replaceAll("\\\\{"));
        }

        boolean allows(String secret) {
            return matchesARegex(secret) || holdsAStopword(secret);
        }

        private boolean matchesARegex(String secret) {
            return regexes.stream().anyMatch(regex -> regex.matcher(secret).find());
        }

        private boolean holdsAStopword(String secret) {
            String lowerCase = secret.toLowerCase(Locale.ROOT);
            return stopwords.stream().anyMatch(lowerCase::contains);
        }
    }
}
