package io.github.ythibos.edi.adapter.fork;

import java.util.List;
import java.util.Objects;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RegisterReflectionForBinding({ForkRoutingRulesReader.RulesDocument.class, ForkRoutingRulesReader.RuleEntry.class})
public class ForkRoutingRulesReader {

    private final JsonMapper json;

    public ForkRoutingRulesReader(JsonMapper json) {
        this.json = json;
    }

    public List<ForkRoutingRule> read(String rules) {
        try {
            return Objects.requireNonNullElse(json.readValue(rules, RulesDocument.class).rules(), List.<RuleEntry>of()).stream()
                    .map(rule -> new ForkRoutingRule(Objects.requireNonNullElse(rule.when(), ""), Objects.requireNonNullElse(rule.why(), "")))
                    .toList();
        } catch (JacksonException unreadable) {
            throw new IllegalArgumentException("the old routing rules cannot be read: " + unreadable.getOriginalMessage(), unreadable);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RulesDocument(List<RuleEntry> rules) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RuleEntry(String when, String why) {
    }
}
