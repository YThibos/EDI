package io.github.ythibos.edi.web.tracker;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class WalkedTicketText implements TextPresenter<WalkedTicket> {

    @Override
    public Class<WalkedTicket> presents() {
        return WalkedTicket.class;
    }

    @Override
    public String text(WalkedTicket walked) {
        if (walked.through().isEmpty()) {
            return walked.key() + " is already in " + walked.from() + ".";
        }
        return walked.key() + " moved " + walked.from() + " -> " + String.join(" -> ", walked.through()) + " in " + walked.through().size()
                + (walked.through().size() == 1 ? " step." : " steps.");
    }
}
