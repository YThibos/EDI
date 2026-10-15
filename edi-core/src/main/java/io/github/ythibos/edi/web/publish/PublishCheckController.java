package io.github.ythibos.edi.web.publish;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.publish.PublishCheck;
import io.github.ythibos.edi.domain.publish.FindingPlace;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;

@RestController
@RegisterReflectionForBinding({
        FindingPlace.CommitPart.class,
        FindingPlace.File.class,
        FindingPlace.Line.class,
        FindingPlace.HiddenFile.class,
        FindingPlace.HiddenLine.class})
class PublishCheckController {

    private final PublishCheck publishCheck;

    PublishCheckController(PublishCheck publishCheck) {
        this.publishCheck = publishCheck;
    }

    @PostMapping("/api/publish-checks")
    ResponseEntity<PublishCheckResult> check(@RequestBody PublishCheckRequest request) {
        RevisionRange outgoing = request.outgoing();
        PublishVerdict verdict = publishCheck.check(request.repositoryPath(), outgoing);
        return ResponseEntity.status(verdict.allowsPush() ? HttpStatus.OK : HttpStatus.CONFLICT)
                .body(new PublishCheckResult(outgoing, verdict));
    }
}
