package io.github.ythibos.edi.domain.board;

public sealed interface BoardReply {

    record Sent() implements BoardReply {
    }

    record NotSent(String reason) implements BoardReply {
    }
}
