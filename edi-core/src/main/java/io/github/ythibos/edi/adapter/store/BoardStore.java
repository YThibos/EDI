package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class BoardStore {

    private static final String SELECT_BOARDS = "select id, task_id, file, rounds, armed_at, ended_at, retired_at from board ";
    private static final String LISTENING = "ended_at is null and retired_at is null";
    private static final String SELECT_ANSWERS = """
            select board_id, round, decision_number, verbatim, option_key, received_at, settled_at, refused_because
            from board_answer
            """;

    private final JdbcClient store;

    public BoardStore(JdbcClient store) {
        this.store = store;
    }

    public Board arm(TaskId task, String file, List<DecisionNumber> bound, Instant at) {
        long id = store.sql("insert into board (task_id, file, rounds, armed_at) values (:task, :file, 0, :at) returning id")
                .param("task", task.value())
                .param("file", file)
                .param("at", Columns.text(at))
                .query((row, _) -> row.getLong(1))
                .single();
        Board armed = new Board(new BoardId(id), task, file, bound, 0, at, null, null);
        bind(armed);
        return armed;
    }

    public void update(Board board) {
        store.sql("update board set rounds = :rounds, ended_at = :ended, retired_at = :retired where id = :id")
                .param("id", board.id().value())
                .param("rounds", board.rounds())
                .param("ended", Columns.text(board.endedAt()))
                .param("retired", Columns.text(board.retiredAt()))
                .update();
        store.sql("delete from board_decision where board_id = :id").param("id", board.id().value()).update();
        bind(board);
    }

    public Optional<Board> get(BoardId id) {
        return boards(store.sql(SELECT_BOARDS + "where id = :id").param("id", id.value())).stream().findFirst();
    }

    public Optional<Board> listeningAt(String file) {
        return boards(store.sql(SELECT_BOARDS + "where file = :file and " + LISTENING).param("file", file)).stream().findFirst();
    }

    public List<Board> listening() {
        return boards(store.sql(SELECT_BOARDS + "where " + LISTENING + " order by id"));
    }

    public List<Board> listeningOf(TaskId task) {
        return boards(store.sql(SELECT_BOARDS + "where task_id = :task and " + LISTENING + " order by id").param("task", task.value()));
    }

    public void save(BoardAnswer answer) {
        store.sql("""
                        insert into board_answer (board_id, round, decision_number, verbatim, option_key, received_at, settled_at, refused_because)
                        values (:board, :round, :decision, :verbatim, :option, :received, :settled, :refused)
                        on conflict (board_id, round, decision_number) do update set
                            settled_at = excluded.settled_at, refused_because = excluded.refused_because""")
                .param("board", answer.board().value())
                .param("round", answer.round())
                .param("decision", answer.decision().value())
                .param("verbatim", answer.verbatim())
                .param("option", Columns.textOf(answer.option()))
                .param("received", Columns.text(answer.receivedAt()))
                .param("settled", Columns.text(answer.settledAt()))
                .param("refused", answer.refusedBecause())
                .update();
    }

    public List<BoardAnswer> unsettledAnswers() {
        return store.sql(SELECT_ANSWERS + "where settled_at is null order by received_at, board_id, round, decision_number")
                .query((row, _) -> answerOf(row))
                .list();
    }

    public List<BoardAnswer> answersOf(BoardId board) {
        return store.sql(SELECT_ANSWERS + "where board_id = :board order by round, decision_number")
                .param("board", board.value())
                .query((row, _) -> answerOf(row))
                .list();
    }

    private void bind(Board board) {
        IntStream.range(0, board.bound().size()).forEach(position -> store.sql("""
                        insert into board_decision (board_id, position, decision_number) values (:board, :position, :decision)""")
                .param("board", board.id().value())
                .param("position", position)
                .param("decision", board.bound().get(position).value())
                .update());
    }

    private List<Board> boards(StatementSpec query) {
        return query.query((row, _) -> unboundBoardOf(row)).list().stream()
                .map(board -> new Board(board.id(), board.task(), board.file(), boundTo(board.id()), board.rounds(), board.armedAt(),
                        board.endedAt(), board.retiredAt()))
                .toList();
    }

    private static Board unboundBoardOf(ResultSet row) throws SQLException {
        return new Board(new BoardId(row.getLong("id")), new TaskId(row.getString("task_id")), row.getString("file"), List.of(),
                row.getInt("rounds"), Columns.instant(row.getString("armed_at")), Columns.instant(row.getString("ended_at")),
                Columns.instant(row.getString("retired_at")));
    }

    private List<DecisionNumber> boundTo(BoardId board) {
        return store.sql("select decision_number from board_decision where board_id = :board order by position")
                .param("board", board.value())
                .query((row, _) -> new DecisionNumber(row.getInt(1)))
                .list();
    }

    private static BoardAnswer answerOf(ResultSet row) throws SQLException {
        String option = row.getString("option_key");
        return new BoardAnswer(new BoardId(row.getLong("board_id")), row.getInt("round"), new DecisionNumber(row.getInt("decision_number")),
                row.getString("verbatim"), option == null ? null : OptionKey.parse(option), Columns.instant(row.getString("received_at")),
                Columns.instant(row.getString("settled_at")), row.getString("refused_because"));
    }
}
