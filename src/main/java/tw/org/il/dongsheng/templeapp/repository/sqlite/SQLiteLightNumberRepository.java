package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.LightNumberRecord;
import tw.org.il.dongsheng.templeapp.model.LightRegistrationReportRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SQLiteLightNumberRepository {
    public static final String STATUS_UNUSED = "N";
    public static final String STATUS_ASSIGNED = "A";
    public static final String STATUS_DELETED = "D";

    private final SQLiteDatabaseManager databaseManager;
    private static final DateTimeFormatter ROC_DATE = DateTimeFormatter.ofPattern("yyy.MM.dd");

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
                        registered_at TEXT,
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
            addColumnIfMissing(connection, "registered_at", "TEXT");
        }
    }

    public List<LightRegistrationReportRow> findRegisteredForReport(String managementType)
            throws SQLException {
        createTable();
        Map<String, LightRegistrationReportRow> rows = new LinkedHashMap<>();
        loadInventoryRegistrations(managementType, rows);
        if (tableExists("donations")) {
            loadDonationRegistrations(managementType, rows);
        }
        if ("LIGHT".equals(managementType) && tableExists("household_light_records")) {
            loadHouseholdRegistrations(rows);
        }
        return rows.values().stream()
                .sorted(Comparator
                        .comparing(LightRegistrationReportRow::lightNumber, Comparator.nullsLast(
                                Comparator.comparing(this::lightPrefix)
                                        .thenComparingInt(this::lightSerial)
                        ))
                        .thenComparing(LightRegistrationReportRow::registrationDate,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
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

    public int countByStatus(
            String managementType,
            String lightType,
            String status
    ) throws SQLException {
        createTable();
        String sql = """
                SELECT COUNT(1)
                FROM light_numbers
                WHERE management_type = ?
                  AND light_type = ?
                  AND status = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, managementType);
            statement.setString(2, lightType);
            statement.setString(3, status);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public Optional<LightNumberRecord> assignFirstUnused(
            String managementType,
            String lightType,
            Integer memberId,
            String principalName,
            String changedBy
    ) throws SQLException {
        createTable();
        String selectSql = """
                SELECT id, serial_number
                FROM light_numbers
                WHERE management_type = ?
                  AND light_type = ?
                  AND status = 'N'
                ORDER BY serial_number
                LIMIT 1
                """;
        String updateSql = """
                UPDATE light_numbers
                SET member_id = ?,
                    principal_name = ?,
                    status = 'A',
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    registered_at = CURRENT_TIMESTAMP,
                    deleted_by = NULL,
                    deleted_at = NULL
                WHERE id = ?
                  AND status = 'N'
                """;
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(selectSql);
                 PreparedStatement update = connection.prepareStatement(updateSql)) {
                select.setString(1, managementType);
                select.setString(2, lightType);
                Integer id = null;
                Integer serialNumber = null;
                try (ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) {
                        id = resultSet.getInt("id");
                        serialNumber = resultSet.getInt("serial_number");
                    }
                }
                if (id == null) {
                    connection.rollback();
                    return Optional.empty();
                }

                update.setObject(1, memberId);
                update.setString(2, principalName);
                update.setString(3, changedBy);
                update.setInt(4, id);
                if (update.executeUpdate() == 0) {
                    connection.rollback();
                    return Optional.empty();
                }
                saveAudit(
                        connection,
                        id,
                        "ASSIGN",
                        STATUS_UNUSED,
                        STATUS_ASSIGNED,
                        changedBy,
                        lightType + String.format("%05d", serialNumber)
                                + " -> member=" + memberId
                );
                connection.commit();
                return Optional.of(new LightNumberRecord(
                        id,
                        managementType,
                        lightType,
                        serialNumber,
                        memberId,
                        principalName,
                        STATUS_ASSIGNED
                ));
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean releaseAssignment(Integer id, String changedBy) throws SQLException {
        createTable();
        if (id == null) {
            return false;
        }
        String updateSql = """
                UPDATE light_numbers
                SET member_id = NULL,
                    principal_name = NULL,
                    status = 'N',
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    registered_at = NULL
                WHERE id = ?
                  AND status = 'A'
                """;
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setString(1, changedBy);
                update.setInt(2, id);
                if (update.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
                saveAudit(
                        connection,
                        id,
                        "RELEASE",
                        STATUS_ASSIGNED,
                        STATUS_UNUSED,
                        changedBy,
                        "點燈紀錄儲存失敗，釋放燈號"
                );
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
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

    private void loadInventoryRegistrations(
            String managementType,
            Map<String, LightRegistrationReportRow> rows
    ) throws SQLException {
        String sql = """
                SELECT ln.light_type || printf('%05d', ln.serial_number) AS light_number,
                       ln.member_id,
                       COALESCE(NULLIF(TRIM(m.name), ''), ln.principal_name, '') AS member_name,
                       m.gender, m.birth_date, m.lunar_birth_date, m.age, m.zodiac, m.zodiac_year,
                       m.birth_time, m.address,
                       COALESCE(ln.registered_at, ln.updated_at, ln.created_at) AS registration_date
                FROM light_numbers ln
                LEFT JOIN light_members m ON m.id = ln.member_id
                WHERE ln.management_type = ?
                  AND ln.status = 'A'
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, managementType);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    putReportRow(rows, resultSet);
                }
            }
        }
    }

    private void loadDonationRegistrations(
            String managementType,
            Map<String, LightRegistrationReportRow> rows
    ) throws SQLException {
        String numberColumn = "TAI_SUI".equals(managementType) ? "d.donor_no" : "d.light_no";
        if (!"TAI_SUI".equals(managementType) && !"LIGHT".equals(managementType)) {
            return;
        }
        String sql = """
                SELECT %s AS light_number,
                       d.member_id,
                       m.name AS member_name,
                       m.gender, m.birth_date, m.lunar_birth_date, m.age, m.zodiac, m.zodiac_year,
                       m.birth_time, m.address,
                       d.donate_date AS registration_date
                FROM donations d
                LEFT JOIN light_members m ON m.id = d.member_id
                WHERE COALESCE(d.is_deleted, 0) = 0
                  AND TRIM(COALESCE(%s, '')) <> ''
                """.formatted(numberColumn, numberColumn);
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                putReportRow(rows, resultSet);
            }
        }
    }

    private void loadHouseholdRegistrations(Map<String, LightRegistrationReportRow> rows)
            throws SQLException {
        String sql = """
                SELECT h.light_no AS light_number,
                       h.member_id,
                       m.name AS member_name,
                       m.gender, m.birth_date, m.lunar_birth_date, m.age, m.zodiac, m.zodiac_year,
                       m.birth_time, m.address,
                       h.created_at AS registration_date
                FROM household_light_records h
                LEFT JOIN light_members m ON m.id = h.member_id
                WHERE TRIM(COALESCE(h.light_no, '')) <> ''
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                putReportRow(rows, resultSet);
            }
        }
    }

    private void putReportRow(
            Map<String, LightRegistrationReportRow> rows,
            ResultSet resultSet
    ) throws SQLException {
        String lightNumber = clean(resultSet.getString("light_number"));
        if (lightNumber.isEmpty()) {
            return;
        }
        int memberIdValue = resultSet.getInt("member_id");
        Integer memberId = resultSet.wasNull() ? null : memberIdValue;
        LocalDate registrationDate = parseDate(resultSet.getString("registration_date"));
        LightRegistrationReportRow row = new LightRegistrationReportRow(
                lightNumber,
                memberId,
                clean(resultSet.getString("member_name")),
                clean(resultSet.getString("gender")),
                clean(resultSet.getString("birth_date")),
                clean(resultSet.getString("lunar_birth_date")),
                nullableInteger(resultSet, "age"),
                clean(resultSet.getString("zodiac")),
                clean(resultSet.getString("zodiac_year")),
                clean(resultSet.getString("birth_time")),
                clean(resultSet.getString("address")),
                registrationDate
        );
        String key = lightNumber + ":" + (memberId == null ? row.name() : memberId)
                + ":" + (registrationDate == null ? "" : registrationDate);
        rows.putIfAbsent(key, row);
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private LocalDate parseDate(String value) {
        String text = clean(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            if (text.matches("\\d{3}\\.\\d{2}\\.\\d{2}")) {
                LocalDate roc = LocalDate.parse(text, ROC_DATE);
                return roc.plusYears(1911);
            }
            return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String lightPrefix(String lightNumber) {
        return clean(lightNumber).replaceFirst("\\d.*$", "");
    }

    private int lightSerial(String lightNumber) {
        String digits = clean(lightNumber).replaceAll("\\D", "");
        try {
            return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void addColumnIfMissing(Connection connection, String columnName, String definition)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(light_numbers)")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE light_numbers ADD COLUMN " + columnName + " " + definition);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
