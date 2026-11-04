package io.github.ythibos.edi.web.publish;

import java.util.Optional;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.publish.PublicationTargets;
import io.github.ythibos.edi.application.publish.PublishCheck;
import io.github.ythibos.edi.domain.publish.FindingPlace;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.NotPermitted;

@RestController
@RegisterReflectionForBinding({
        FindingPlace.CommitPart.class,
        FindingPlace.File.class,
        FindingPlace.Line.class,
        FindingPlace.HiddenFile.class,
        FindingPlace.HiddenLine.class})
class PublishCheckController {

    private static final String CONSOLE_REFUSAL = "edi publish-check runs under the EDI home's own token or a worker's, never from the console.";

    private final PublishCheck publishCheck;
    private final PublicationTargets targets;

    PublishCheckController(PublishCheck publishCheck, PublicationTargets targets) {
        this.publishCheck = publishCheck;
        this.targets = targets;
    }

    @PostMapping("/api/publish-checks")
    ResponseEntity<PublishCheckResult> check(@RequestBody PublishCheckRequest request, Caller caller) {
        targets.requireCheckable(request.repositoryPath(), workersOwnTask(caller));
        RevisionRange outgoing = request.outgoing();
        PublishVerdict verdict = request.namesThePushTarget()
                ? publishCheck.checkPush(request.repositoryPath(), outgoing, request.remoteUrl())
                : publishCheck.check(request.repositoryPath(), outgoing);
        return ResponseEntity.status(verdict.allowsPush() ? HttpStatus.OK : HttpStatus.CONFLICT)
                .body(new PublishCheckResult(outgoing, verdict));
    }

    private static Optional<TaskId> workersOwnTask(Caller caller) {
        return switch (caller) {
            case Caller.WorkerCaller worker -> Optional.of(worker.worker().task());
            case Caller.Primary _, Caller.Standby _ -> Optional.empty();
            case Caller.Captain _ -> throw new NotPermitted(CONSOLE_REFUSAL);
        };
    }
}
