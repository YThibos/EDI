package io.github.ythibos.edi.application.dispatch;

import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.CategoryId;

public record DispatchRequest(CategoryId category, String spec, List<String> expectedFiles, CaptainOverride captainsOverride) {

    public DispatchRequest {
        spec = Objects.requireNonNullElse(spec, "");
        expectedFiles = List.copyOf(Objects.requireNonNullElse(expectedFiles, List.of()));
    }
}
