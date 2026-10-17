package io.github.ythibos.edi.adapter.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import io.github.ythibos.edi.domain.branch.BranchName;

final class PipelineState {

    private PipelineState() {
    }

    static Connection readOnly(Path stateDatabase) throws SQLException {
        SQLiteConfig settings = new SQLiteConfig();
        settings.setReadOnly(true);
        SQLiteDataSource database = new SQLiteDataSource(settings);
        database.setUrl("jdbc:sqlite:" + stateDatabase);
        return database.getConnection();
    }

    static void bindTheBranch(PreparedStatement query, Path projectClone, BranchName branch) throws SQLException {
        query.setString(1, realPathOf(projectClone).toString());
        query.setString(2, branch.toString());
    }

    private static Path realPathOf(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException absent) {
            return directory.toAbsolutePath().normalize();
        }
    }
}
