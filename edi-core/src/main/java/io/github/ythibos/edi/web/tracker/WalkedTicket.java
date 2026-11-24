package io.github.ythibos.edi.web.tracker;

import java.util.List;

import io.github.ythibos.edi.domain.project.WorkKey;

record WalkedTicket(WorkKey key, String from, List<String> through) {

    WalkedTicket {
        through = List.copyOf(through);
    }
}
