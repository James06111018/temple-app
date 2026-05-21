package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.CustomChar;
import tw.org.il.dongsheng.templeapp.repository.CustomCharRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteCustomCharRepository implements CustomCharRepository {
    private static final String TABLE_NAME = "custom_chars";

    private static final String[] DEFAULT_CHARS = {
            "喆", "栢", "峯", "啓", "羣", "尢", "娣", "妍", "衍", "瑅",
            "咊", "淮", "珉", "僖", "彣", "緒", "綉", "梡", "咏", "沛",
            "臻", "頡", "粮", "枣", "牟", "廍", "沂", "焜", "仔", "趂",
            "叁", "苙", "丞", "栃", "眞", "温"
    };

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteCustomCharRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "code TEXT NOT NULL UNIQUE," +
                "char_value TEXT," +
                "note TEXT," +
                "enabled INTEGER DEFAULT 1," +
                "updated_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                ")";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public void seedDefaults() throws SQLException {
        createTable();
        String sql = "INSERT OR IGNORE INTO " + TABLE_NAME + " (code, char_value, note, enabled) VALUES (?, ?, ?, 1)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < DEFAULT_CHARS.length; i++) {
                statement.setString(1, codeAt(i));
                statement.setString(2, DEFAULT_CHARS[i]);
                statement.setString(3, "");
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public List<CustomChar> findEnabled() throws SQLException {
        createTable();
        String sql = "SELECT id, code, char_value, note, enabled FROM " + TABLE_NAME + " WHERE enabled = 1 ORDER BY code";
        List<CustomChar> chars = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                chars.add(new CustomChar(
                        resultSet.getInt("id"),
                        resultSet.getString("code"),
                        resultSet.getString("char_value"),
                        resultSet.getString("note"),
                        resultSet.getInt("enabled") == 1
                ));
            }
        }

        return chars;
    }

    private String codeAt(int index) {
        return String.format("FA%02X", 0x40 + index);
    }
}
