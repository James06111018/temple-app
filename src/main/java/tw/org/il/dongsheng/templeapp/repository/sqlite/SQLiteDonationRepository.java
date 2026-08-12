package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.AuthSession;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationAuditRecord;
import tw.org.il.dongsheng.templeapp.repository.DonationRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SQLiteDonationRepository implements DonationRepository {
    private static final String TABLE_NAME = "donations";
    public static final String AUDIT_ACTION_UPDATE = "UPDATE";
    public static final String AUDIT_ACTION_DELETE = "DELETE";

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteDonationRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createTable() throws SQLException {
//        String dropSql = "DROP TABLE IF EXISTS " + TABLE_NAME;
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "member_id INTEGER NOT NULL," +
                "receipt_no TEXT," +
                "donate_date TEXT," +
                "extra_no TEXT," +
                "amount INTEGER," +
                "summary TEXT," +
                "donate_note TEXT," +
                "other_note TEXT," +
                "donor_no TEXT," +
                "light_no TEXT," +
                "should_pay INTEGER," +
                "donate_type TEXT," +
                "creator TEXT," +
                "is_deleted INTEGER NOT NULL DEFAULT 0," +
                "FOREIGN KEY(member_id) REFERENCES light_members(id) ON DELETE CASCADE" +
                ")";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
//            statement.execute(dropSql);
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute(sql);
            addColumnIfMissing(connection, "is_deleted", "INTEGER NOT NULL DEFAULT 0");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_donations_is_deleted ON donations(is_deleted)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS donation_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        donation_id INTEGER,
                        member_id INTEGER,
                        action TEXT NOT NULL,
                        changed_by TEXT,
                        changed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        snapshot TEXT
                    )
                    """);
        }
    }

    @Override
    public Donation save(Donation donation) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " (" +
                "member_id, receipt_no, donate_date, extra_no, amount, summary, donate_note, other_note, donor_no, light_no, should_pay, donate_type, creator" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            setCommonFields(statement, donation);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    donation.setId(generatedKeys.getInt(1));
                }
            }
        }
        saveAudit(donation.getId(), donation.getMemberId(), "CREATE",
                AuthSession.getCurrentOperatorName(), donation.toString());

        return donation;
    }

    @Override
    public boolean update(Donation donation) throws SQLException {
        if (donation.getId() == null) {
            throw new IllegalArgumentException("Donation id cannot be null when updating.");
        }

        String sql = "UPDATE " + TABLE_NAME + " SET " +
                "member_id = ?, receipt_no = ?, donate_date = ?, extra_no = ?, amount = ?, summary = ?, donate_note = ?, other_note = ?, donor_no = ?, " +
                "light_no = ?, should_pay = ?, donate_type = ?, creator = ? " +
                "WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        Optional<Donation> before = findById(donation.getId());
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            setCommonFields(statement, donation);
            statement.setObject(14, donation.getId());
            boolean updated = statement.executeUpdate() > 0;
            if (updated) {
                saveAudit(donation.getId(), donation.getMemberId(), "UPDATE",
                        AuthSession.getCurrentOperatorName(),
                        "before=" + before.map(Donation::toString).orElse("") + "\nafter=" + donation);
            }
            return updated;
        }
    }

    @Override
    public boolean deleteById(int id) throws SQLException {
        Optional<Donation> before = findById(id);
        String sql = "UPDATE " + TABLE_NAME +
                " SET is_deleted = 1 WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            boolean deleted = statement.executeUpdate() > 0;
            if (deleted) {
                saveAudit(
                        id,
                        before.map(Donation::getMemberId).orElse(null),
                        "DELETE",
                        AuthSession.getCurrentOperatorName(),
                        before.map(Donation::toString).orElse("")
                );
            }
            return deleted;
        }
    }

    @Override
    public Optional<Donation> findById(int id) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public List<Donation> findByMemberId(int memberId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE member_id = ? AND COALESCE(is_deleted, 0) = 0 ORDER BY donate_date DESC, id DESC";
        List<Donation> donations = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, memberId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    donations.add(mapRow(resultSet));
                }
            }
        }

        return donations;
    }

    @Override
    public List<Donation> findByMemberIds(List<Integer> memberIds, int limit, int offset) throws SQLException {
        List<Donation> donations = new ArrayList<>();

        if (memberIds == null || memberIds.isEmpty()) return donations;

        // 1. 根據 ID 數量產生等量的問號，例如 "?, ?, ?"
        String placeholders = memberIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));

        // 2. 組合 SQL
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE member_id IN (" + placeholders + ") AND COALESCE(is_deleted, 0) = 0 " +
                "ORDER BY donate_date DESC, id DESC " +
                "LIMIT ? OFFSET ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            // 3. 設定 IN 裡面的參數
            int index = 1;
            for (Integer id : memberIds) {
                statement.setInt(index++, id);
            }

            // 4. 設定分頁參數 (注意順序要在 IN 之後)
            statement.setInt(index++, limit);
            statement.setInt(index++, offset);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    donations.add(mapRow(resultSet));
                }
            }
        }
        return donations;
    }

    @Override
    public int getDonationCount(List<Integer> memberIds) throws SQLException {
        if (memberIds == null || memberIds.isEmpty()) return 0;

        // 1. 根據 ID 數量產生等量的問號，例如 "?, ?, ?"
        String placeholders = memberIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));

        // 2. 組合 SQL
        String sql = "SELECT COUNT(1) FROM " + TABLE_NAME +
                " WHERE member_id IN (" + placeholders + ") AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            // 3. 設定 IN 裡面的參數
            int index = 1;
            for (Integer id : memberIds) {
                statement.setInt(index++, id);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<Donation> findAll() throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE COALESCE(is_deleted, 0) = 0 ORDER BY donate_date DESC, id DESC";
        List<Donation> donations = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                donations.add(mapRow(resultSet));
            }
        }

        return donations;
    }

    public List<Donation> findByIds(List<Integer> donationIds) throws SQLException {
        if (donationIds == null || donationIds.isEmpty()) {
            return List.of();
        }

        String placeholders = donationIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));
        String sql = "SELECT * FROM " + TABLE_NAME
                + " WHERE id IN (" + placeholders + ")"
                + " AND COALESCE(is_deleted, 0) = 0 ORDER BY id";
        List<Donation> donations = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Integer donationId : donationIds) {
                statement.setInt(index++, donationId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    donations.add(mapRow(resultSet));
                }
            }
        }
        return donations;
    }

    public List<DonationAuditRecord> findAuditRecordsByDateRange(
            String action,
            LocalDate startDate,
            LocalDate endDate
    ) throws SQLException {
        createTable();
        String sql = """
                SELECT a.id AS audit_id,
                       a.donation_id,
                       a.member_id AS audit_member_id,
                       a.action,
                       a.changed_by,
                       a.changed_at,
                       a.snapshot,
                       m.name AS member_name,
                       d.id AS current_id,
                       d.member_id AS current_member_id,
                       d.receipt_no AS current_receipt_no,
                       d.donate_date AS current_donate_date,
                       d.extra_no AS current_extra_no,
                       d.amount AS current_amount,
                       d.summary AS current_summary,
                       d.donate_note AS current_donate_note,
                       d.other_note AS current_other_note,
                       d.donor_no AS current_donor_no,
                       d.light_no AS current_light_no,
                       d.should_pay AS current_should_pay,
                       d.donate_type AS current_donate_type,
                       d.creator AS current_creator
                FROM donation_audits a
                LEFT JOIN donations d ON d.id = a.donation_id
                LEFT JOIN light_members m ON m.id = a.member_id
                WHERE a.action = ?
                  AND date(a.changed_at) BETWEEN ? AND ?
                ORDER BY a.changed_at, a.id
                """;
        List<DonationAuditRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, action);
            statement.setString(2, startDate.toString());
            statement.setString(3, endDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String snapshot = resultSet.getString("snapshot");
                    Donation current = mapCurrentDonation(resultSet);
                    Donation before = parseBeforeDonation(snapshot);
                    Donation after = parseAfterDonation(snapshot);
                    if (AUDIT_ACTION_DELETE.equals(action) && before == null) {
                        before = current;
                    }
                    if (AUDIT_ACTION_UPDATE.equals(action) && after == null) {
                        after = current;
                    }
                    if (before != null && before.getCreator() == null && current != null) {
                        before.setCreator(current.getCreator());
                    }
                    if (after != null && after.getCreator() == null && current != null) {
                        after.setCreator(current.getCreator());
                    }
                    records.add(new DonationAuditRecord(
                            resultSet.getInt("audit_id"),
                            getNullableInt(resultSet, "donation_id"),
                            getNullableInt(resultSet, "audit_member_id"),
                            resultSet.getString("action"),
                            resultSet.getString("changed_by"),
                            resultSet.getString("changed_at"),
                            resultSet.getString("member_name"),
                            before,
                            after,
                            extractReason(snapshot)
                    ));
                }
            }
        }
        return records;
    }

    private void setCommonFields(PreparedStatement statement, Donation donation) throws SQLException {
        statement.setObject(1, donation.getMemberId());
        statement.setString(2, donation.getReceiptNo());
        statement.setString(3, donation.getDonateDate());
        statement.setString(4, donation.getExtraNo());
        statement.setObject(5, donation.getAmount());
        statement.setString(6, donation.getSummary());
        statement.setString(7, donation.getDonateNote());
        statement.setString(8, donation.getOtherNote());
        statement.setString(9, donation.getDonorNo());
        statement.setString(10, donation.getLightNo());
        statement.setObject(11, donation.getShouldPay());
        statement.setString(12, donation.getDonateType());
        statement.setString(13, donation.getCreator());
    }

    private void saveAudit(Integer donationId, Integer memberId, String action, String changedBy, String snapshot) throws SQLException {
        String sql = """
                INSERT INTO donation_audits (donation_id, member_id, action, changed_by, snapshot)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, donationId);
            statement.setObject(2, memberId);
            statement.setString(3, action);
            statement.setString(4, changedBy);
            statement.setString(5, snapshot);
            statement.executeUpdate();
        }
    }

    private void addColumnIfMissing(Connection connection, String columnName, String definition) throws SQLException {
        boolean exists = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + TABLE_NAME + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + definition);
            }
        }
    }

    private Donation mapRow(ResultSet resultSet) throws SQLException {
        Donation donation = new Donation();
        donation.setId(resultSet.getInt("id"));
        donation.setMemberId(resultSet.getInt("member_id"));
        donation.setReceiptNo(resultSet.getString("receipt_no"));
        donation.setDonateDate(resultSet.getString("donate_date"));
        donation.setExtraNo(resultSet.getString("extra_no"));
        donation.setAmount((Integer) resultSet.getObject("amount"));
        donation.setSummary(resultSet.getString("summary"));
        donation.setDonateNote(resultSet.getString("donate_note"));
        donation.setOtherNote(resultSet.getString("other_note"));
        donation.setDonorNo(resultSet.getString("donor_no"));
        donation.setLightNo(resultSet.getString("light_no"));
        donation.setShouldPay((Integer) resultSet.getObject("should_pay"));
        donation.setDonateType(resultSet.getString("donate_type"));
        donation.setCreator(resultSet.getString("creator"));
        return donation;
    }

    private Donation mapCurrentDonation(ResultSet resultSet) throws SQLException {
        Integer id = getNullableInt(resultSet, "current_id");
        if (id == null) {
            return null;
        }
        Donation donation = new Donation();
        donation.setId(id);
        donation.setMemberId(getNullableInt(resultSet, "current_member_id"));
        donation.setReceiptNo(resultSet.getString("current_receipt_no"));
        donation.setDonateDate(resultSet.getString("current_donate_date"));
        donation.setExtraNo(resultSet.getString("current_extra_no"));
        donation.setAmount(getNullableInt(resultSet, "current_amount"));
        donation.setSummary(resultSet.getString("current_summary"));
        donation.setDonateNote(resultSet.getString("current_donate_note"));
        donation.setOtherNote(resultSet.getString("current_other_note"));
        donation.setDonorNo(resultSet.getString("current_donor_no"));
        donation.setLightNo(resultSet.getString("current_light_no"));
        donation.setShouldPay(getNullableInt(resultSet, "current_should_pay"));
        donation.setDonateType(resultSet.getString("current_donate_type"));
        donation.setCreator(resultSet.getString("current_creator"));
        return donation;
    }

    private Integer getNullableInt(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private Donation parseBeforeDonation(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        int afterIndex = snapshot.indexOf("after=Donation{");
        String beforePart = afterIndex >= 0 ? snapshot.substring(0, afterIndex) : snapshot;
        return parseDonationSnapshot(beforePart);
    }

    private Donation parseAfterDonation(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        int afterIndex = snapshot.indexOf("after=Donation{");
        return afterIndex < 0 ? null : parseDonationSnapshot(snapshot.substring(afterIndex));
    }

    private Donation parseDonationSnapshot(String snapshot) {
        int start = snapshot.indexOf("Donation{");
        int end = snapshot.indexOf('}', start);
        if (start < 0 || end < 0) {
            return null;
        }
        String value = snapshot.substring(start, end + 1);
        Donation donation = new Donation();
        donation.setId(extractInteger(value, "id"));
        donation.setMemberId(extractInteger(value, "memberId"));
        donation.setReceiptNo(extractQuoted(value, "receiptNo"));
        donation.setDonateDate(extractQuoted(value, "donateDate"));
        donation.setExtraNo(extractQuoted(value, "extraNo"));
        donation.setAmount(extractInteger(value, "amount"));
        donation.setSummary(extractQuoted(value, "summary"));
        donation.setDonateNote(extractQuoted(value, "donateNote"));
        donation.setOtherNote(extractQuoted(value, "otherNote"));
        donation.setDonorNo(extractQuoted(value, "donorNo"));
        donation.setLightNo(extractQuoted(value, "lightNo"));
        donation.setShouldPay(extractInteger(value, "shouldPay"));
        donation.setDonateType(extractQuoted(value, "donateType"));
        donation.setCreator(extractQuoted(value, "creator"));
        return donation;
    }

    private Integer extractInteger(String snapshot, String field) {
        Matcher matcher = Pattern.compile("(?:^|[,{ ])" + Pattern.quote(field) + "=(-?\\d+|null)")
                .matcher(snapshot);
        if (!matcher.find() || "null".equals(matcher.group(1))) {
            return null;
        }
        return Integer.valueOf(matcher.group(1));
    }

    private String extractQuoted(String snapshot, String field) {
        Matcher matcher = Pattern.compile("(?:^|[,{ ])" + Pattern.quote(field) + "='(.*?)'(?=, [A-Za-z]|})")
                .matcher(snapshot);
        if (!matcher.find()) {
            return null;
        }
        return "null".equals(matcher.group(1)) ? null : matcher.group(1);
    }

    private String extractReason(String snapshot) {
        if (snapshot == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("(?:^|\\R)reason=(.*)$", Pattern.MULTILINE).matcher(snapshot);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
