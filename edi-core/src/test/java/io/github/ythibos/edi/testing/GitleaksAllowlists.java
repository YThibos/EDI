package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import tools.jackson.dataformat.toml.TomlMapper;

final class GitleaksAllowlists {

    static final String AWS_ACCESS_TOKEN = "aws-access-token";
    static final String GITHUB_PAT = "github-pat";

    private static final String DEFAULT_CONFIGURATION_EXCERPT = "gitleaks/default-allowlists.toml";
    private static final Pattern GO_LITERAL_OPENING_BRACE = Pattern.compile("(?<!\\\\)\\{(?!\\d+(?:,\\d*)?})");

    private final String version;
    private final Allowlist global;
    private final Map<String, List<Allowlist>> ofRules;

    private GitleaksAllowlists(DefaultConfiguration configuration) {
        this.version = configuration.version();
        this.global = configuration.allowlist();
        this.ofRules = configuration.rules().stream().collect(Collectors.toMap(Rule::id, Rule::allowlists));
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

    boolean allow(String rule, String secret) {
        return allowlistsOf(rule).anyMatch(allowlist -> allowlist.allows(secret));
    }

    private Stream<Allowlist> allowlistsOf(String rule) {
        return Stream.concat(Stream.of(global), ofRules.getOrDefault(rule, List.of()).stream());
    }

    private static Pattern inJavaSyntax(String goRegex) {
        return Pattern.compile(GO_LITERAL_OPENING_BRACE.matcher(goRegex).replaceAll("\\\\{"));
    }

    private record DefaultConfiguration(String version, Allowlist allowlist, List<Rule> rules) {
    }

    private record Rule(String id, List<Allowlist> allowlists) {
    }

    private record Allowlist(List<Pattern> regexes, List<String> stopwords) {

        @JsonCreator
        static Allowlist of(
                @JsonProperty("regexes") @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> regexes,
                @JsonProperty("stopwords") @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> stopwords) {
            return new Allowlist(regexes.stream().map(GitleaksAllowlists::inJavaSyntax).toList(), stopwords);
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
