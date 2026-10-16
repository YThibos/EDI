package io.github.ythibos.edi.adapter.fork;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForkOption(String key, String text) {
}
