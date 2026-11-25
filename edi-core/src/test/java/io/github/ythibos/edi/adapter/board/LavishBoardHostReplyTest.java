package io.github.ythibos.edi.adapter.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.domain.board.BoardReply;
import io.github.ythibos.edi.testing.StandInScripts;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class LavishBoardHostReplyTest {

    @TempDir
    private Path directory;

    @Test
    void repliesToTheBoardWithTheWordsAsTheAgentReply() throws IOException {
        Path board = Files.writeString(directory.resolve("board.html"), "<!doctype html>\n");
        Path arguments = directory.resolve("arguments");
        Path lavish = StandInScripts.installed(directory.resolve("bin/lavish-axi"), "#!/bin/sh\nprintf '%s\\n' \"$@\" > '" + arguments + "'\n");

        BoardReply reply = hostRunning(lavish).reply(board, "Received 2 answers in round 1. 2 of them go to D58, D61.");

        assertThat(reply).isEqualTo(new BoardReply.Sent());
        assertThat(Files.readAllLines(arguments)).containsExactly("reply", board.toString(), "--agent-reply", "Received 2 answers in round 1. 2 of them go to D58, D61.");
    }

    @Test
    void reportsAReplyTheCommandRefusedWithItsExitAndItsComplaint() throws IOException {
        Path board = Files.writeString(directory.resolve("board.html"), "<!doctype html>\n");
        Path lavish = StandInScripts.installed(directory.resolve("bin/lavish-axi"), "#!/bin/sh\necho 'no open session' >&2\nexit 3\n");

        BoardReply reply = hostRunning(lavish).reply(board, "Received 1 answer in round 1. It answers no decision.");

        assertThat(reply).isEqualTo(new BoardReply.NotSent("lavish-axi reply exited with 3: no open session"));
    }

    @Test
    void reportsAReplyWhoseCommandCannotStart() {
        Path board = directory.resolve("board.html");

        BoardReply reply = hostRunning(directory.resolve("bin/missing-lavish-axi")).reply(board, "Received 1 answer in round 1. It answers no decision.");

        assertThat(reply).isInstanceOfSatisfying(BoardReply.NotSent.class, notSent -> assertThat(notSent.reason()).contains("could not run"));
    }

    @Test
    void reportsAReplyInterruptedWhileItRunsAndLeavesTheInterruptStanding() throws IOException {
        Path board = Files.writeString(directory.resolve("board.html"), "<!doctype html>\n");
        Path lavish = StandInScripts.installed(directory.resolve("bin/lavish-axi"), "#!/bin/sh\nsleep 60\n");

        Thread.currentThread().interrupt();
        BoardReply reply;
        boolean interruptStanding;
        try {
            reply = hostRunning(lavish).reply(board, "Received 1 answer in round 1. It answers no decision.");
        } finally {
            interruptStanding = Thread.interrupted();
        }

        assertThat(reply).isInstanceOfSatisfying(BoardReply.NotSent.class, notSent -> assertThat(notSent.reason()).contains("was interrupted"));
        assertThat(interruptStanding).isTrue();
    }

    private static LavishBoardHost hostRunning(Path lavish) {
        return new LavishBoardHost(new ExternalPrograms(), new JsonMapper(), lavish.toString());
    }
}
