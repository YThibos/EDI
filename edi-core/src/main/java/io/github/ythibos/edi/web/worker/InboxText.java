package io.github.ythibos.edi.web.worker;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class InboxText implements TextPresenter<InboxMessages> {

    @Override
    public Class<InboxMessages> presents() {
        return InboxMessages.class;
    }

    @Override
    public String text(InboxMessages inbox) {
        if (inbox.messages().isEmpty()) {
            return "No message waits for you.\n";
        }
        return inbox.messages().stream().map(InboxText::message).collect(Collectors.joining("\n", "", ""))
                + "Acknowledge each message with edi inbox ack <n> once you have acted on it.\n";
    }

    private static String message(InboxMessage message) {
        String heading = "Message " + message.number() + (message.resolves() == null ? "" : ", resolving " + message.resolves()) + ":\n";
        return heading + message.body() + "\n";
    }
}
