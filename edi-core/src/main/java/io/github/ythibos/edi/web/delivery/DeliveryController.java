package io.github.ythibos.edi.web.delivery;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.delivery.CleanUpTask;
import io.github.ythibos.edi.application.delivery.DeliverDraft;
import io.github.ythibos.edi.application.delivery.FollowMerges;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class DeliveryController {

    private final DeliverDraft deliverDraft;
    private final FollowMerges followMerges;
    private final CleanUpTask cleanUpTask;

    DeliveryController(DeliverDraft deliverDraft, FollowMerges followMerges, CleanUpTask cleanUpTask) {
        this.deliverDraft = deliverDraft;
        this.followMerges = followMerges;
        this.cleanUpTask = cleanUpTask;
    }

    @PostMapping("/api/tasks/{reference}/deliver")
    Confirmation deliver(@PathVariable String reference, Caller caller) {
        caller.requirePrimary("edi deliver");
        return new Confirmation.DraftDelivered(deliverDraft.deliver(TaskReference.parse(reference), caller.actor()));
    }

    @PostMapping("/api/merged")
    Confirmation merged(@RequestBody MergedBody body, Caller caller) {
        caller.requirePrimary("edi merged");
        return new Confirmation.MergedWorkFollowedUp(followMerges.mergedSaysTheCaptain(body.requiredReference(), caller.actor()));
    }

    @PostMapping("/api/tasks/{reference}/cleanup")
    Confirmation cleanUp(@PathVariable String reference, Caller caller) {
        caller.requirePrimary("edi cleanup");
        return new Confirmation.CleanedUp(cleanUpTask.cleanUp(TaskReference.parse(reference), caller.actor()));
    }
}
