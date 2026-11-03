package io.github.ythibos.edi.web.wake;

import java.util.List;
import java.util.UUID;

import io.github.ythibos.edi.application.wake.StreamPoll;

record WaitRequest(UUID stream, Long pid, Boolean follows, List<Long> printed, Boolean ending) {

    StreamPoll poll() {
        return new StreamPoll(stream, pid == null ? 0 : pid, Boolean.TRUE.equals(follows), printed, Boolean.TRUE.equals(ending));
    }
}
