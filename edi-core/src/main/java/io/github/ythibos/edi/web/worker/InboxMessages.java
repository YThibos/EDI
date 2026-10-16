package io.github.ythibos.edi.web.worker;

import java.util.List;

import io.github.ythibos.edi.domain.inbox.InboxMessage;

record InboxMessages(List<InboxMessage> messages) {

    InboxMessages {
        messages = List.copyOf(messages);
    }
}
