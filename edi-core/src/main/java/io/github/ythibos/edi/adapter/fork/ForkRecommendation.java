package io.github.ythibos.edi.adapter.fork;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForkRecommendation(String key, String why) {
}
