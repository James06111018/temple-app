package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.CreateRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SQLiteCreateRecordRepository {

    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "id", "name", "phone", "city", "dist", "address", "zipCode",
            "birthDate", "lunarBirthDate", "age", "zodiac", "zodiacYear",
            "birthTime", "note", "contactPerson", "idNumber", "sortOrder",
            "ding", "kou", "isMail", "gender"
    );
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DATABASE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private final SQLiteDatabaseManager databaseManager;

    public SQLiteCreateRecordRepository() {
        this(SQLiteDatabaseManager.getInstance());
    }

    SQLiteCreateRecordRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<CreateRecord> findByMemberId(int memberId) {
        return query("a.member_id = ?", statement -> statement.setInt(1, memberId));
    }

    public List<CreateRecord> findByDateRange(LocalDate start, LocalDate end) {
        return query("a.changed_at >= ? AND a.changed_at < ?", statement -> {
            statement.setString(1, toDatabaseTime(start));
            statement.setString(2, toDatabaseTime(end.plusDays(1)));
        });
    }

    public List<CreateRecord> findByName(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        return query("1 = 1", statement -> {
        }).stream().filter(record -> record.name().contains(normalized)).toList();
    }

    private List<CreateRecord> query(String whereClause, StatementBinder binder) {
        String sql = """
                SELECT a.id AS audit_id, a.member_id, a.action, a.changed_by,
                       a.changed_at, a.snapshot,
                       m.name AS current_name, m.phone AS current_phone,
                       m.city AS current_city, m.dist AS current_dist,
                       m.address AS current_address, m.zip_code AS current_zip_code,
                       m.birth_date AS current_birth_date,
                       m.lunar_birth_date AS current_lunar_birth_date,
                       m.zodiac AS current_zodiac, m.zodiac_year AS current_zodiac_year,
                       m.birth_time AS current_birth_time, m.note AS current_note,
                       m.id_number AS current_id_number, m.sort_order AS current_sort_order,
                       m.is_mail AS current_is_mail, m.gender AS current_gender
                  FROM light_member_audits a
             LEFT JOIN light_members m ON m.id = a.member_id
                 WHERE %s
              ORDER BY datetime(a.changed_at) ASC, a.id ASC
                """.formatted(whereClause);

        List<CreateRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(toRecord(resultSet));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("查詢建檔記錄失敗", ex);
        }
        return records;
    }

    private CreateRecord toRecord(ResultSet resultSet) throws SQLException {
        String rawSnapshot = resultSet.getString("snapshot");
        Map<String, String> values = parseSnapshot(rawSnapshot);
        if ((rawSnapshot == null || rawSnapshot.isBlank()) && values.isEmpty()) {
            values = currentMemberValues(resultSet);
        }

        LocalDateTime changedAt = parseChangedAt(resultSet.getString("changed_at"));
        String memberNumber = formatMemberNumber(resultSet.getLong("member_id"));
        String fullAddress = value(values, "city") + value(values, "dist") + value(values, "address");
        return new CreateRecord(
                resultSet.getLong("audit_id"),
                formatRocDate(changedAt.toLocalDate()),
                changedAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                displayAction(resultSet.getString("action")),
                blankIfNull(resultSet.getString("changed_by")),
                memberNumber,
                value(values, "name"),
                value(values, "birthDate"),
                value(values, "lunarBirthDate"),
                value(values, "zodiac"),
                value(values, "zodiacYear"),
                value(values, "birthTime"),
                value(values, "gender"),
                fullAddress,
                value(values, "phone"),
                value(values, "zipCode"),
                value(values, "isMail"),
                value(values, "note"),
                "",
                value(values, "idNumber"),
                formatSortOrder(value(values, "sortOrder"))
        );
    }

    static Map<String, String> parseSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return Map.of();
        }
        int memberStart = snapshot.lastIndexOf("Member{");
        if (memberStart < 0) {
            return Map.of();
        }
        int bodyStart = memberStart + "Member{".length();
        int bodyEnd = snapshot.lastIndexOf('}');
        String body = snapshot.substring(bodyStart, bodyEnd < bodyStart ? snapshot.length() : bodyEnd);
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < SNAPSHOT_FIELDS.size(); index++) {
            String field = SNAPSHOT_FIELDS.get(index);
            String marker = field + "=";
            int valueStart = body.indexOf(marker);
            if (valueStart < 0) {
                continue;
            }
            valueStart += marker.length();
            int valueEnd = body.length();
            if (index + 1 < SNAPSHOT_FIELDS.size()) {
                String nextMarker = ", " + SNAPSHOT_FIELDS.get(index + 1) + "=";
                int next = body.indexOf(nextMarker, valueStart);
                if (next >= 0) {
                    valueEnd = next;
                }
            }
            values.put(field, cleanSnapshotValue(body.substring(valueStart, valueEnd)));
        }
        return values;
    }

    private static Map<String, String> currentMemberValues(ResultSet resultSet) throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", blankIfNull(resultSet.getString("current_name")));
        values.put("phone", blankIfNull(resultSet.getString("current_phone")));
        values.put("city", blankIfNull(resultSet.getString("current_city")));
        values.put("dist", blankIfNull(resultSet.getString("current_dist")));
        values.put("address", blankIfNull(resultSet.getString("current_address")));
        values.put("zipCode", blankIfNull(resultSet.getString("current_zip_code")));
        values.put("birthDate", blankIfNull(resultSet.getString("current_birth_date")));
        values.put("lunarBirthDate", blankIfNull(resultSet.getString("current_lunar_birth_date")));
        values.put("zodiac", blankIfNull(resultSet.getString("current_zodiac")));
        values.put("zodiacYear", blankIfNull(resultSet.getString("current_zodiac_year")));
        values.put("birthTime", blankIfNull(resultSet.getString("current_birth_time")));
        values.put("note", blankIfNull(resultSet.getString("current_note")));
        values.put("idNumber", blankIfNull(resultSet.getString("current_id_number")));
        values.put("sortOrder", blankIfNull(resultSet.getString("current_sort_order")));
        values.put("isMail", blankIfNull(resultSet.getString("current_is_mail")));
        values.put("gender", blankIfNull(resultSet.getString("current_gender")));
        return values;
    }

    private static LocalDateTime parseChangedAt(String text) {
        if (text == null || text.isBlank()) {
            return LocalDateTime.now(TAIPEI);
        }
        try {
            LocalDateTime utc = LocalDateTime.parse(text, DATABASE_TIME);
            return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(TAIPEI).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(text.replace(' ', 'T'));
            } catch (RuntimeException ignored) {
                return LocalDateTime.now(TAIPEI);
            }
        }
    }

    static String toDatabaseTime(LocalDate date) {
        return date.atStartOfDay(TAIPEI)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .format(DATABASE_TIME);
    }

    private static String displayAction(String action) {
        if (action == null) {
            return "";
        }
        return switch (action.toUpperCase(Locale.ROOT)) {
            case "CREATE" -> "存檔";
            case "UPDATE" -> "存檔";
            case "RESERVE" -> "新增";
            case "DELETE" -> "刪除";
            default -> action;
        };
    }

    private static String cleanSnapshotValue(String value) {
        String result = value == null ? "" : value.trim();
        if (result.length() >= 2 && result.startsWith("'") && result.endsWith("'")) {
            result = result.substring(1, result.length() - 1);
        }
        return "null".equalsIgnoreCase(result) ? "" : result;
    }

    private static String value(Map<String, String> values, String key) {
        return blankIfNull(values.get(key));
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String formatMemberNumber(long memberId) {
        return memberId <= 0 ? "" : String.format(Locale.ROOT, "%07d", memberId);
    }

    private static String formatSortOrder(String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank()) {
            return "";
        }
        try {
            return String.format(Locale.ROOT, "%03d", Integer.parseInt(sortOrder));
        } catch (NumberFormatException ex) {
            return sortOrder;
        }
    }

    private static String formatRocDate(LocalDate date) {
        return String.format(Locale.ROOT, "%03d.%02d.%02d",
                date.getYear() - 1911, date.getMonthValue(), date.getDayOfMonth());
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
