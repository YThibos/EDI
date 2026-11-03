package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.authority.AnsweredQuestion;
import io.github.ythibos.edi.domain.authority.CaptainMessage;
import io.github.ythibos.edi.domain.authority.CaptainMessages;
import io.github.ythibos.edi.domain.authority.DialogAnswer;
import io.github.ythibos.edi.domain.authority.TypedMessage;

@Component
public class CaptainMessageStore {

    private static final String TYPED = "typed";
    private static final String DIALOG_ANSWER = "dialog-answer";

    private final JdbcClient store;

    public CaptainMessageStore(JdbcClient store) {
        this.store = store;
    }

    public TypedMessage recordTyped(UUID session, String text, Instant sentAt) {
        TypedMessage message = new TypedMessage(session, nextNumberIn(session), text, sentAt);
        store.sql("insert into captain_message (session_id, number, kind, text, sent_at) values (:session, :number, :kind, :text, :sent)")
                .param("session", session.toString())
                .param("number", message.number())
                .param("kind", TYPED)
                .param("text", message.text())
                .param("sent", Columns.text(sentAt))
                .update();
        return message;
    }

    public DialogAnswer recordDialogAnswer(UUID session, AnsweredQuestion answer, Instant sentAt) {
        DialogAnswer message = new DialogAnswer(session, nextNumberIn(session), answer, sentAt);
        store.sql("insert into captain_message (session_id, number, kind, question, chosen, notes, sent_at)"
                        + " values (:session, :number, :kind, :question, :chosen, :notes, :sent)")
                .param("session", session.toString())
                .param("number", message.number())
                .param("kind", DIALOG_ANSWER)
                .param("question", answer.question())
                .param("chosen", answer.chosen())
                .param("notes", answer.notes())
                .param("sent", Columns.text(sentAt))
                .update();
        return message;
    }

    public CaptainMessages sentIn(UUID session) {
        return new CaptainMessages(store.sql("select session_id, number, kind, text, question, chosen, notes, sent_at from captain_message where session_id = :session order by number")
                .param("session", session.toString())
                .query((row, _) -> message(row))
                .list());
    }

    private int nextNumberIn(UUID session) {
        return store.sql("select coalesce(max(number), 0) + 1 from captain_message where session_id = :session")
                .param("session", session.toString())
                .query((row, _) -> row.getInt(1))
                .single();
    }

    private static CaptainMessage message(ResultSet row) throws SQLException {
        UUID session = UUID.fromString(row.getString("session_id"));
        int number = row.getInt("number");
        Instant sentAt = Columns.instant(row.getString("sent_at"));
        return TYPED.equals(row.getString("kind"))
                ? new TypedMessage(session, number, row.getString("text"), sentAt)
                : new DialogAnswer(session, number, new AnsweredQuestion(row.getString("question"), row.getString("chosen"), row.getString("notes")),
                        sentAt);
    }
}
