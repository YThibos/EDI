package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.console.ConsoleSession;
import io.github.ythibos.edi.domain.console.SignInLink;

@Component
public class ConsoleAccessStore {

    private final JdbcClient store;

    public ConsoleAccessStore(JdbcClient store) {
        this.store = store;
    }

    public void save(SignInLink link) {
        store.sql("""
                        insert into console_sign_in (digest, issued_at, expires_at, used_at) values (:digest, :issued, :expires, :used)
                        on conflict (digest) do update set used_at = excluded.used_at""")
                .param("digest", link.digest())
                .param("issued", Columns.text(link.issuedAt()))
                .param("expires", Columns.text(link.expiresAt()))
                .param("used", Columns.text(link.usedAt()))
                .update();
    }

    public Optional<SignInLink> signInLink(String digest) {
        return store.sql("select digest, issued_at, expires_at, used_at from console_sign_in where digest = :digest")
                .param("digest", digest)
                .query((row, _) -> new SignInLink(row.getString("digest"), Columns.instant(row.getString("issued_at")),
                        Columns.instant(row.getString("expires_at")), Columns.instant(row.getString("used_at"))))
                .optional();
    }

    public void save(ConsoleSession session) {
        store.sql("insert into console_session (digest, opened_at, expires_at) values (:digest, :opened, :expires)")
                .param("digest", session.digest())
                .param("opened", Columns.text(session.openedAt()))
                .param("expires", Columns.text(session.expiresAt()))
                .update();
    }

    public Optional<ConsoleSession> session(String digest) {
        return store.sql("select digest, opened_at, expires_at from console_session where digest = :digest")
                .param("digest", digest)
                .query((row, _) -> new ConsoleSession(row.getString("digest"), Columns.instant(row.getString("opened_at")),
                        Columns.instant(row.getString("expires_at"))))
                .optional();
    }

    public void forgetExpiredBefore(Instant now) {
        store.sql("delete from console_sign_in where expires_at < :now").param("now", Columns.text(now)).update();
        store.sql("delete from console_session where expires_at < :now").param("now", Columns.text(now)).update();
    }
}
