package io.github.ythibos.edi.web.recovery;

import static io.github.ythibos.edi.web.RequestValues.optional;

import io.github.ythibos.edi.application.supervision.RecoverFromOutage.RouteChange;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;

record RecoveryAnswer(String model, String effort) {

    RouteChange routeChange() {
        return new RouteChange(optional(model, ModelId::new), optional(effort, EffortLevel::withinTheCeiling));
    }
}
