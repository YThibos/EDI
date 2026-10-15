package io.github.ythibos.edi.adapter.gitleaks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.ythibos.edi.domain.publish.PublishFinding;
import io.github.ythibos.edi.domain.publish.PublishRule;

@JsonIgnoreProperties(ignoreUnknown = true)
record GitleaksFinding(
        @JsonProperty("RuleID") String rule,
        @JsonProperty("File") String file,
        @JsonProperty("StartLine") int line,
        @JsonProperty("Commit") String commit) {

    PublishFinding asPublishFinding() {
        return PublishFinding.onLine(PublishRule.SECRET, file, line, commit, "gitleaks rule " + rule);
    }
}
