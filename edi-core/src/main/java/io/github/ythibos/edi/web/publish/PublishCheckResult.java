package io.github.ythibos.edi.web.publish;

import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;

record PublishCheckResult(RevisionRange range, PublishVerdict verdict) {
}
