package io.github.ythibos.edi.adapter.tracker;

public class TrackerUnavailable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TrackerUnavailable(String reason) {
        super(reason);
    }
}
