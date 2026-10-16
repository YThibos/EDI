package io.github.ythibos.edi.web;

import io.github.ythibos.edi.application.delivery.DeliverDraft.DeliveredDraft;
import io.github.ythibos.edi.application.delivery.FollowMerges.MergedWork;
import io.github.ythibos.edi.domain.task.Task;

final class DeliverySentences {

    private DeliverySentences() {
    }

    static String delivered(DeliveredDraft delivered) {
        String how = delivered.opened() ? "Opened the draft " : "Recorded the open draft ";
        return how + delivered.delivery().address() + " for " + delivered.delivery().task() + "; EDI watches it until the captain merges it.";
    }

    static String followedUp(MergedWork work) {
        String merge = work.mergeRequest() == null
                ? "The captain merged " + work.task() + " locally"
                : "Recorded the merge of " + work.mergeRequest().address() + " as " + work.mergeRequest().landedCommit();
        String cleanUp = work.cleanUp().done()
                ? work.task() + " is cleaned up and done."
                : work.task() + " is not cleaned up yet: " + work.cleanUp().refusal();
        return merge + "; " + cleanUp;
    }

    static String cleanedUp(Task task) {
        return "Cleaned up " + task.id() + ": its work is landed, its isolated copy and branch are removed, and the task is done.";
    }
}
