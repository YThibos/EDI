package io.github.ythibos.edi.application.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryRound {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryRound.class);

    private final ObservePipelines pipelines;
    private final FollowMerges merges;

    public DeliveryRound(ObservePipelines pipelines, FollowMerges merges) {
        this.pipelines = pipelines;
        this.merges = merges;
    }

    @Scheduled(fixedDelayString = "${edi.delivery.pipelines:PT60S}", initialDelayString = "${edi.delivery.pipelines:PT60S}")
    public void watchThePipelines() {
        attempt("watch the pipelines", pipelines::observe);
    }

    @Scheduled(fixedDelayString = "${edi.delivery.drafts:PT10M}", initialDelayString = "${edi.delivery.drafts:PT10M}")
    public void watchTheDrafts() {
        attempt("watch the open drafts", merges::watchTheOpenDrafts);
        attempt("finish merges that waited for their clone", merges::finishEarlierMerges);
    }

    private static void attempt(String part, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException failed) {
            LOG.warn("The delivery round could not {}: {}", part, failed.getMessage());
        }
    }
}
