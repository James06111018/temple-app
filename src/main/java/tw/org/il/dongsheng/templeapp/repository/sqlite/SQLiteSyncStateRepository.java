package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.sync.SyncConflictPolicy;
import tw.org.il.dongsheng.templeapp.sync.SyncState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteSyncStateRepository {
    private final SQLiteDatabaseManager databaseManager;

    public SQLiteSyncStateRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createTable() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sync_state (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        last_pull_at TEXT,
                        last_push_at TEXT,
                        last_sync_at TEXT,
                        last_sync_token TEXT,
                        device_id TEXT NOT NULL,
                        conflict_policy TEXT NOT NULL DEFAULT 'LAST_WRITE_WINS',
                        remote_base_url TEXT,
                        updated_at TEXT
                    )
                    """);
            statement.execute("""
                    INSERT INTO sync_state (id, device_id, conflict_policy, updated_at)
                    VALUES (1, 'local', 'LAST_WRITE_WINS', CURRENT_TIMESTAMP)
                    ON CONFLICT(id) DO NOTHING
                    """);
        }
    }

    public SyncState load() throws SQLException {
        createTable();
        String sql = "SELECT * FROM sync_state WHERE id = 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                SyncState state = new SyncState();
                state.setLastPullAt(resultSet.getString("last_pull_at"));
                state.setLastPushAt(resultSet.getString("last_push_at"));
                state.setLastSyncAt(resultSet.getString("last_sync_at"));
                state.setLastSyncToken(resultSet.getString("last_sync_token"));
                state.setDeviceId(resultSet.getString("device_id"));
                state.setConflictPolicy(SyncConflictPolicy.valueOf(
                        resultSet.getString("conflict_policy") == null
                                ? "LAST_WRITE_WINS"
                                : resultSet.getString("conflict_policy")));
                state.setRemoteBaseUrl(resultSet.getString("remote_base_url"));
                state.setUpdatedAt(resultSet.getString("updated_at"));
                return state;
            }
        }
        return new SyncState();
    }

    public void save(SyncState state) throws SQLException {
        createTable();
        String sql = """
                UPDATE sync_state
                   SET last_pull_at = ?,
                       last_push_at = ?,
                       last_sync_at = ?,
                       last_sync_token = ?,
                       device_id = ?,
                       conflict_policy = ?,
                       remote_base_url = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = 1
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, state.getLastPullAt());
            statement.setString(2, state.getLastPushAt());
            statement.setString(3, state.getLastSyncAt());
            statement.setString(4, state.getLastSyncToken());
            statement.setString(5, state.getDeviceId());
            statement.setString(6, state.getConflictPolicy().name());
            statement.setString(7, state.getRemoteBaseUrl());
            statement.executeUpdate();
        }
    }
}
