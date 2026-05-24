package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.HouseholdLightRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteHouseholdLightRepository {
    private final SQLiteDatabaseManager databaseManager;

    public SQLiteHouseholdLightRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createTable() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS household_light_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        member_id INTEGER NOT NULL,
                        light_type_id INTEGER NOT NULL,
                        roc_year INTEGER NOT NULL,
                        light_no TEXT,
                        note TEXT,
                        created_by TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_by TEXT,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(member_id, light_type_id, roc_year),
                        FOREIGN KEY(member_id) REFERENCES light_members(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS household_light_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        target_table TEXT NOT NULL,
                        target_id INTEGER,
                        action TEXT NOT NULL,
                        changed_by TEXT,
                        changed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        note TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_household_light_records_year ON household_light_records(roc_year)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_household_light_records_member ON household_light_records(member_id)");
        }
    }

    public List<HouseholdLightRecord> findRecordsByMembersAndYear(List<Integer> memberIds, int rocYear) throws SQLException {
        createTable();
        List<HouseholdLightRecord> records = new ArrayList<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return records;
        }

        String placeholders = String.join(",", memberIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT id, member_id, light_type_id, roc_year, light_no, note, created_by, created_at, updated_by, updated_at
                FROM household_light_records
                WHERE roc_year = ?
                  AND member_id IN (%s)
                ORDER BY member_id, light_type_id
                """.formatted(placeholders);

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, rocYear);
            int index = 2;
            for (Integer memberId : memberIds) {
                statement.setInt(index++, memberId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
        }
        return records;
    }

    public HouseholdLightRecord saveRecord(HouseholdLightRecord record, String changedBy) throws SQLException {
        createTable();
        String sql = """
                INSERT INTO household_light_records (member_id, light_type_id, roc_year, light_no, note, created_by, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(member_id, light_type_id, roc_year)
                DO UPDATE SET light_no = excluded.light_no,
                              note = excluded.note,
                              updated_by = excluded.updated_by,
                              updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, record.getMemberId());
            statement.setObject(2, record.getLightTypeId());
            statement.setObject(3, record.getRocYear());
            statement.setString(4, record.getLightNo());
            statement.setString(5, record.getNote());
            statement.setString(6, changedBy);
            statement.setString(7, changedBy);
            statement.executeUpdate();
        }
        saveAudit("household_light_records", record.getId(), "UPSERT", changedBy,
                "member=" + record.getMemberId() + ", type=" + record.getLightTypeId());
        return record;
    }

    public boolean deleteRecord(int memberId, int lightTypeId, int rocYear, String changedBy) throws SQLException {
        createTable();
        String sql = """
                DELETE FROM household_light_records
                WHERE member_id = ?
                  AND light_type_id = ?
                  AND roc_year = ?
                """;
        boolean deleted;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, memberId);
            statement.setInt(2, lightTypeId);
            statement.setInt(3, rocYear);
            deleted = statement.executeUpdate() > 0;
        }
        if (deleted) {
            saveAudit("household_light_records", null, "DELETE", changedBy,
                    "member=" + memberId + ", type=" + lightTypeId + ", year=" + rocYear);
        }
        return deleted;
    }

    private void saveAudit(String table, Integer targetId, String action, String changedBy, String note) throws SQLException {
        String sql = """
                INSERT INTO household_light_audits (target_table, target_id, action, changed_by, note)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setObject(2, targetId);
            statement.setString(3, action);
            statement.setString(4, changedBy);
            statement.setString(5, note);
            statement.executeUpdate();
        }
    }

    private HouseholdLightRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new HouseholdLightRecord(
                resultSet.getInt("id"),
                resultSet.getInt("member_id"),
                resultSet.getInt("light_type_id"),
                resultSet.getInt("roc_year"),
                resultSet.getString("light_no"),
                resultSet.getString("note"),
                resultSet.getString("created_by"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_by"),
                resultSet.getString("updated_at")
        );
    }
}
