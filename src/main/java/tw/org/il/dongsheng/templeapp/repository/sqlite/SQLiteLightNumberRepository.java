package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.LightNumberRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteLightNumberRepository {
    public static final String STATUS_UNUSED = "N";
    public static final String STATUS_ASSIGNED = "A";
    public static final String STATUS_DELETED = "D";

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteLightNumberRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createTable() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS light_numbers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        management_type TEXT NOT NULL,
                        light_type TEXT NOT NULL,
                        serial_number INTEGER NOT NULL,
                        member_id INTEGER,
                        principal_name TEXT,
                        status TEXT NOT NULL DEFAULT 'N',
                        created_by TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_by TEXT,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        deleted_by TEXT,
                        deleted_at TEXT,
                        UNIQUE(management_type, light_type, serial_number),
                        FOREIGN KEY(member_id) REFERENCES light_members(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS light_number_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        light_number_id INTEGER,
                        action TEXT NOT NULL,
                        old_status TEXT,
                        new_status TEXT,
                        changed_by TEXT,
                        changed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        detail TEXT
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_light_numbers_query
                    ON light_numbers(management_type, light_type, status, serial_number)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_light_number_audits_target
                    ON light_number_audits(light_number_id, changed_at)
                    """);
        }
    }

    public List<LightNumberRecord> find(
            String managementType,
            String lightType,
            String numberQuery,
            String status
    ) throws SQLException {
        createTable();
        String query = numberQuery == null ? "" : numberQuery.trim();
        String statusCode = status == null ? "" : status.trim();
        String sql = """
                SELECT id, management_type, light_type, serial_number,
                       member_id, principal_name, status
                FROM light_numbers
                WHERE management_type = ?
                  AND light_type = ?
                  AND (
                      ? = ''
                      OR light_type || printf('%05d', serial_number) LIKE '%' || ? || '%'
                      OR CAST(serial_number AS TEXT) LIKE '%' || ? || '%'
                  )
                  AND (? = '' OR status = ?)
                ORDER BY serial_number
                """;
        List<LightNumberRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, managementType);
            statement.setString(2, lightType);
            statement.setString(3, query);
            statement.setString(4, query);
            statement.setString(5, query);
            statement.setString(6, statusCode);
            statement.setString(7, statusCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
        }
        return records;
    }

    public int addRange(
            String managementType,
            String lightType,
            int startNumber,
            int endNumber,
            String changedBy
    ) throws SQLException {
        createTable();
        String insertSql = """
                INSERT OR IGNORE INTO light_numbers
                    (management_type, light_type, serial_number, status, created_by, updated_by)
                VALUES (?, ?, ?, 'N', ?, ?)
                """;
        String idSql = """
                SELECT id
                FROM light_numbers
                WHERE management_type = ? AND light_type = ? AND serial_number = ?
                """;

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            int inserted = 0;
            try (PreparedStatement insert = connection.prepareStatement(insertSql);
                 PreparedStatement findId = connection.prepareStatement(idSql)) {
                for (int number = startNumber; number <= endNumber; number++) {
                    insert.setString(1, managementType);
                    insert.setString(2, lightType);
                    insert.setInt(3, number);
                    insert.setString(4, changedBy);
                    insert.setString(5, changedBy);
                    if (insert.executeUpdate() == 0) {
                        continue;
                    }

                    findId.setString(1, managementType);
                    findId.setString(2, lightType);
                    findId.setInt(3, number);
                    try (ResultSet resultSet = findId.executeQuery()) {
                        if (resultSet.next()) {
                            saveAudit(
                                    connection,
                                    resultSet.getInt("id"),
                                    "CREATE",
                                    null,
                                    STATUS_UNUSED,
                                    changedBy,
                                    lightType + String.format("%05d", number)
                            );
                        }
                    }
                    inserted++;
                }
                connection.commit();
                return inserted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public int softDelete(List<Integer> ids, String changedBy) throws SQLException {
        return updateStatuses(ids, STATUS_DELETED, "DELETE", changedBy, "單筆刪除");
    }

    public int restoreDeleted(List<Integer> ids, String changedBy) throws SQLException {
        return updateStatuses(ids, STATUS_UNUSED, "RESTORE", changedBy, "已刪除轉未使用");
    }

    public int softDeleteEndingInFour(
            String managementType,
            String lightType,
            String changedBy
    ) throws SQLException {
        return softDeleteByCondition(
                managementType,
                lightType,
                "serial_number % 10 = 4",
                "DELETE_SUFFIX_4",
                changedBy,
                "刪除尾數為 4 的號碼"
        );
    }

    public int softDeleteContainingFour(
            String managementType,
            String lightType,
            String changedBy
    ) throws SQLException {
        return softDeleteByCondition(
                managementType,
                lightType,
                "CAST(serial_number AS TEXT) LIKE '%4%'",
                "DELETE_CONTAINS_4",
                changedBy,
                "刪除含有 4 的號碼"
        );
    }

    private int softDeleteByCondition(
            String managementType,
            String lightType,
            String condition,
            String action,
            String changedBy,
            String detail
    ) throws SQLException {
        createTable();
        String sql = """
                SELECT id
                FROM light_numbers
                WHERE management_type = ?
                  AND light_type = ?
                  AND status <> 'D'
                  AND %s
                ORDER BY serial_number
                """.formatted(condition);
        List<Integer> ids = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, managementType);
            statement.setString(2, lightType);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getInt("id"));
                }
            }
        }
        return updateStatuses(ids, STATUS_DELETED, action, changedBy, detail);
    }

    private int updateStatuses(
            List<Integer> ids,
            String newStatus,
            String action,
            String changedBy,
            String detail
    ) throws SQLException {
        createTable();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String selectSql = "SELECT status FROM light_numbers WHERE id = ?";
        String updateSql = """
                UPDATE light_numbers
                SET status = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    deleted_by = CASE WHEN ? = 'D' THEN ? ELSE NULL END,
                    deleted_at = CASE WHEN ? = 'D' THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE id = ? AND status <> ?
                """;
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            int updated = 0;
            try (PreparedStatement select = connection.prepareStatement(selectSql);
                 PreparedStatement update = connection.prepareStatement(updateSql)) {
                for (Integer id : ids) {
                    select.setInt(1, id);
                    String oldStatus;
                    try (ResultSet resultSet = select.executeQuery()) {
                        if (!resultSet.next()) {
                            continue;
                        }
                        oldStatus = resultSet.getString("status");
                    }
                    if (newStatus.equals(oldStatus)) {
                        continue;
                    }

                    update.setString(1, newStatus);
                    update.setString(2, changedBy);
                    update.setString(3, newStatus);
                    update.setString(4, changedBy);
                    update.setString(5, newStatus);
                    update.setInt(6, id);
                    update.setString(7, newStatus);
                    if (update.executeUpdate() > 0) {
                        saveAudit(
                                connection,
                                id,
                                action,
                                oldStatus,
                                newStatus,
                                changedBy,
                                detail
                        );
                        updated++;
                    }
                }
                connection.commit();
                return updated;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void saveAudit(
            Connection connection,
            Integer lightNumberId,
            String action,
            String oldStatus,
            String newStatus,
            String changedBy,
            String detail
    ) throws SQLException {
        String sql = """
                INSERT INTO light_number_audits
                    (light_number_id, action, old_status, new_status, changed_by, detail)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, lightNumberId);
            statement.setString(2, action);
            statement.setString(3, oldStatus);
            statement.setString(4, newStatus);
            statement.setString(5, changedBy);
            statement.setString(6, detail);
            statement.executeUpdate();
        }
    }

    private LightNumberRecord mapRecord(ResultSet resultSet) throws SQLException {
        int memberIdValue = resultSet.getInt("member_id");
        Integer memberId = resultSet.wasNull() ? null : memberIdValue;
        return new LightNumberRecord(
                resultSet.getInt("id"),
                resultSet.getString("management_type"),
                resultSet.getString("light_type"),
                resultSet.getInt("serial_number"),
                memberId,
                resultSet.getString("principal_name"),
                resultSet.getString("status")
        );
    }
}
