package io.github.ythibos.edi.adapter.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record RegisteredTracker(String projectKey) {
}
