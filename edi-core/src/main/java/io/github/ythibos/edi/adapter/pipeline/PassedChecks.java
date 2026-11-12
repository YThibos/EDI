package io.github.ythibos.edi.adapter.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class PassedChecks {

    private static final String READINESS_OF_A_RUN = "select ci_ready_at from runs where id = ?";

    private PassedChecks() {
    }

    static boolean recordedIn(Path stateDatabase, String run) {
        if (!Files.isRegularFile(stateDatabase)) {
            return false;
        }
        try (Connection database = PipelineState.readOnly(stateDatabase);
                PreparedStatement query = database.prepareStatement(READINESS_OF_A_RUN)) {
            query.setString(1, run);
            return readyIn(query);
        } catch (SQLException unreadable) {
            return false;
        }
    }

    private static boolean readyIn(PreparedStatement query) throws SQLException {
        try (ResultSet readiness = query.executeQuery()) {
            return readiness.next() && readiness.getObject("ci_ready_at") != null;
        }
    }
}
