package io.github.ythibos.edi.web.dispatch;

import static io.github.ythibos.edi.web.RequestValues.optional;

import java.util.List;

import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;

record DispatchBody(String category, String spec, List<String> files, String overrideModel, String overrideEffort, String reason) {

    DispatchRequest request() {
        return new DispatchRequest(optional(category, CategoryId::new), spec, files, captainsOverride());
    }

    private CaptainOverride captainsOverride() {
        ModelId model = optional(overrideModel, ModelId::new);
        EffortLevel effort = optional(overrideEffort, EffortLevel::named);
        if (model == null) {
            if (effort != null || reason != null) {
                throw new IllegalArgumentException("An override of the dispatch rules names its model with --override-model.");
            }
            return null;
        }
        return new CaptainOverride(new Route(model, effort), reason);
    }
}
