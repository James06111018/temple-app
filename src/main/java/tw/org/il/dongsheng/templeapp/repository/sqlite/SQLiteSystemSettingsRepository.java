package tw.org.il.dongsheng.templeapp.repository.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public class SQLiteSystemSettingsRepository {
    private final SQLiteDatabaseManager databaseManager;

    public SQLiteSystemSettingsRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createTable() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS system_settings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        setting_group TEXT NOT NULL,
                        setting_key TEXT NOT NULL,
                        setting_value TEXT,
                        updated_by TEXT,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(setting_group, setting_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS system_settings_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        setting_group TEXT NOT NULL,
                        setting_key TEXT NOT NULL,
                        old_value TEXT,
                        new_value TEXT,
                        changed_by TEXT,
                        changed_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    public Map<String, String> findByGroup(String group) throws SQLException {
        createTable();
        Map<String, String> values = new LinkedHashMap<>();
        String sql = "SELECT setting_key, setting_value FROM system_settings WHERE setting_group = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, group);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.put(resultSet.getString("setting_key"), resultSet.getString("setting_value"));
                }
            }
        }
        return values;
    }

    public void saveGroup(String group, Map<String, String> values, String changedBy) throws SQLException {
        createTable();
        Map<String, String> before = findByGroup(group);
        String sql = """
                INSERT INTO system_settings (setting_group, setting_key, setting_value, updated_by, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(setting_group, setting_key)
                DO UPDATE SET setting_value = excluded.setting_value,
                              updated_by = excluded.updated_by,
                              updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                statement.setString(1, group);
                statement.setString(2, entry.getKey());
                statement.setString(3, entry.getValue());
                statement.setString(4, changedBy);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        saveAudit(group, before, values, changedBy);
    }

    private void saveAudit(String group, Map<String, String> before, Map<String, String> after, String changedBy) throws SQLException {
        String sql = """
                INSERT INTO system_settings_audits (setting_group, setting_key, old_value, new_value, changed_by)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : after.entrySet()) {
                statement.setString(1, group);
                statement.setString(2, entry.getKey());
                statement.setString(3, before.get(entry.getKey()));
                statement.setString(4, entry.getValue());
                statement.setString(5, changedBy);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
