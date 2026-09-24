package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.AuthSession;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationAuditRecord;
import tw.org.il.dongsheng.templeapp.model.DonationRankingRow;
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
import java.util.UUID;

import static tw.org.il.dongsheng.templeapp.util.Util.convertToDbDateString;

public class SQLiteDonationRepository implements DonationRepository {
    private static final String TABLE_NAME = "donations";
    public static final String AUDIT_ACTION_UPDATE = "UPDATE";
    public static final String AUDIT_ACTION_DELETE = "DELETE";
    public static final String AUDIT_ACTION_RECEIPT_SUPPLEMENT = "RECEIPT_SUPPLEMENT";

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteDonationRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public SQLiteDatabaseManager getDatabaseManager() {
        return databaseManager;
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
                "uuid TEXT UNIQUE," +
                "updated_at TEXT," +
                "deleted_at TEXT," +
                "version INTEGER NOT NULL DEFAULT 1," +
                "device_id TEXT," +
                "sync_status TEXT NOT NULL DEFAULT 'clean'," +
                "FOREIGN KEY(member_id) REFERENCES light_members(id) ON DELETE CASCADE" +
                ")";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
//            statement.execute(dropSql);
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute(sql);
            addColumnIfMissing(connection, "is_deleted", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "uuid", "TEXT");
            addColumnIfMissing(connection, "updated_at", "TEXT");
            addColumnIfMissing(connection, "deleted_at", "TEXT");
            addColumnIfMissing(connection, "version", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(connection, "device_id", "TEXT");
            addColumnIfMissing(connection, "sync_status", "TEXT NOT NULL DEFAULT 'clean'");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_donations_is_deleted ON donations(is_deleted)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_donations_uuid_unique ON donations(uuid)");
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
            addAuditColumnIfMissing(connection, "member_name_at_change", "TEXT");
            addAuditColumnIfMissing(connection, "before_recorded", "INTEGER NOT NULL DEFAULT 0");
            addAuditColumnIfMissing(connection, "before_receipt_no", "TEXT");
            addAuditColumnIfMissing(connection, "before_donate_date", "TEXT");
            addAuditColumnIfMissing(connection, "before_amount", "INTEGER");
            addAuditColumnIfMissing(connection, "before_donate_type", "TEXT");
            addAuditColumnIfMissing(connection, "before_creator", "TEXT");
            addAuditColumnIfMissing(connection, "after_recorded", "INTEGER NOT NULL DEFAULT 0");
            addAuditColumnIfMissing(connection, "after_receipt_no", "TEXT");
            addAuditColumnIfMissing(connection, "after_donate_date", "TEXT");
            addAuditColumnIfMissing(connection, "after_amount", "INTEGER");
            addAuditColumnIfMissing(connection, "after_donate_type", "TEXT");
            addAuditColumnIfMissing(connection, "after_creator", "TEXT");
            addAuditColumnIfMissing(connection, "reason", "TEXT");
            addAuditColumnIfMissing(connection, "supplement_receipt_no", "TEXT");
        }
    }

    @Override
    public Donation save(Donation donation) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " (" +
                "member_id, receipt_no, donate_date, extra_no, amount, summary, donate_note, other_note, donor_no, light_no, should_pay, donate_type, creator" +
                ", uuid, updated_at, deleted_at, version, device_id, sync_status" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            setCommonFields(statement, donation);
            applySyncDefaults(donation);
            setSyncFields(statement, donation);
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
                ", updated_at = ?, deleted_at = ?, version = ?, device_id = ?, sync_status = ? " +
                "WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                connection.createStatement().execute("PRAGMA foreign_keys = ON");
                Optional<Donation> before = findById(connection, donation.getId());
                if (before.isEmpty()) {
                    connection.rollback();
                    return false;
                }

                boolean updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    setCommonFields(statement, donation);
                    bumpSyncVersion(donation);
                    setSyncFields(statement, donation);
                    statement.setObject(19, donation.getId());
                    updated = statement.executeUpdate() > 0;
                }
                if (updated) {
                    saveUpdateAudit(
                            connection,
                            before.get(),
                            donation,
                            AuthSession.getCurrentOperatorName(),
                            findMemberName(connection, donation.getMemberId())
                    );
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

    @Override
    public boolean deleteById(int id, String reason) throws SQLException {
        requireReason(reason);
        createTable();
        String sql = "UPDATE " + TABLE_NAME +
                " SET is_deleted = 1, deleted_at = ?, updated_at = ?, version = COALESCE(version, 1) + 1, sync_status = 'deleted' WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Donation> before = findById(connection, id);
                if (before.isEmpty()) {
                    connection.rollback();
                    return false;
                }

                boolean deleted;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, tw.org.il.dongsheng.templeapp.util.Util.nowUtc());
                    statement.setString(2, tw.org.il.dongsheng.templeapp.util.Util.nowUtc());
                    statement.setInt(3, id);
                    deleted = statement.executeUpdate() > 0;
                }
                if (deleted) {
                    saveActionAudit(
                            connection,
                            AUDIT_ACTION_DELETE,
                            before.get(),
                            reason,
                            null,
                            AuthSession.getCurrentOperatorName(),
                            findMemberName(connection, before.get().getMemberId())
                    );
                }
                connection.commit();
                return deleted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean supplementReceipt(int id, String reason, String supplementReceiptNo) throws SQLException {
        requireReason(reason);
        requireSupplementReceiptNo(supplementReceiptNo);
        createTable();
        try (Connection connection = databaseManager.getConnection()) {
            Optional<Donation> donation = findById(connection, id);
            if (donation.isEmpty()) {
                return false;
            }
            saveActionAudit(
                    connection,
                    AUDIT_ACTION_RECEIPT_SUPPLEMENT,
                    donation.get(),
                    reason,
                    supplementReceiptNo.trim(),
                    AuthSession.getCurrentOperatorName(),
                    findMemberName(connection, donation.get().getMemberId())
            );
            return true;
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
    public List<Donation> findAll(LocalDate startDate, LocalDate endDate, String receiptNo, String creator) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_NAME + " WHERE COALESCE(is_deleted, 0) = 0");
        List<Object> params = new ArrayList<>();
        if (startDate != null) {
            sql.append(" AND donate_date >= ?");
            params.add(convertToDbDateString(startDate));
        }
        if (endDate != null) {
            sql.append(" AND donate_date <= ?");
            params.add(convertToDbDateString(endDate));
        }
        if (receiptNo != null && !receiptNo.trim().isEmpty()) {
            sql.append(" AND receipt_no >= ?");
            params.add(receiptNo);
        }
        if (creator != null && !creator.trim().isEmpty()) {
            sql.append(" AND creator = ?");
            params.add(creator);
        }

        sql.append(" ORDER BY donate_date DESC, id DESC");
        List<Donation> donations = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                // JDBC 的索引是從 1 開始計算，所以是 i + 1
                statement.setObject(i + 1, params.get(i));
            }

            // 注意 2：參數設定完後，才執行查詢取得 ResultSet
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    donations.add(mapRow(resultSet));
                }
            }
        }

        return donations;
    }

    @Override
    public List<DonationRankingRow> findRanking(int limit) throws SQLException {
        int resultLimit = Math.max(1, limit);
        String sql = """
                SELECT m.id AS member_id,
                       m.name AS member_name,
                       SUM(COALESCE(d.amount, d.should_pay, 0)) AS total_amount,
                       COUNT(d.id) AS total_count
                FROM donations d
                JOIN light_members m ON m.id = d.member_id
                WHERE COALESCE(d.is_deleted, 0) = 0
                  AND COALESCE(m.is_deleted, 0) = 0
                  AND TRIM(COALESCE(m.name, '')) <> ''
                GROUP BY m.id, m.name
                ORDER BY total_amount DESC, total_count DESC, m.id ASC
                LIMIT ?
                """;
        List<DonationRankingRow> rows = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, resultLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                int rank = 1;
                while (resultSet.next()) {
                    rows.add(new DonationRankingRow(
                            rank++,
                            resultSet.getInt("member_id"),
                            resultSet.getString("member_name"),
                            resultSet.getLong("total_amount"),
                            resultSet.getInt("total_count")
                    ));
                }
            }
        }
        return rows;
    }

    public List<Donation> findByIds(List<Integer> donationIds, String receiptNo) throws SQLException {
        if (donationIds == null || donationIds.isEmpty()) {
            return List.of();
        }

        String placeholders = donationIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_NAME
                + " WHERE id IN (" + placeholders + ")");

        List<Object> params = new ArrayList<>();
        if (receiptNo != null && !receiptNo.trim().isEmpty()) {
            sql.append(" AND receipt_no >= ?");
            params.add(receiptNo);
        }

        sql.append(" AND COALESCE(is_deleted, 0) = 0 ORDER BY id");

        List<Donation> donations = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (Integer donationId : donationIds) {
                statement.setInt(index++, donationId);
            }
            for (int i = index; i < (index+params.size()); i++) {
                // JDBC 的索引是從 1 開始計算，所以是 i + 1
                statement.setObject(i + 1, params.get(i));
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
            LocalDate endDate,
            String receiptNo,
            String creator
    ) throws SQLException {
        createTable();
        StringBuilder sql = new StringBuilder("""
                SELECT a.id AS audit_id,
                a.donation_id,
                a.member_id AS audit_member_id,
                a.action,
                a.changed_by,
                a.changed_at,
                a.snapshot,
                a.reason,
                a.supplement_receipt_no,
                COALESCE(a.member_name_at_change, m.name) AS member_name,
                a.before_recorded,
                a.before_receipt_no,
                a.before_donate_date,
                a.before_amount,
                a.before_donate_type,
                a.before_creator,
                a.after_recorded,
                a.after_receipt_no,
                a.after_donate_date,
                a.after_amount,
                a.after_donate_type,
                a.after_creator,
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
                LEFT JOIN light_members m ON m.id = a.member_id """);

        List<Object> params = new ArrayList<>();
        sql.append(" WHERE a.action = ?");
        params.add(action);

        if (startDate != null) {
            sql.append(" AND date(a.changed_at) >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND date(a.changed_at) <= ?");
            params.add(endDate);
        }
        if (receiptNo != null && !receiptNo.trim().isEmpty()) {
            sql.append(" AND a.before_receipt_no >= ?");
            params.add(receiptNo);
        }
        if (creator != null && !creator.trim().isEmpty()) {
            sql.append(" AND (a.before_creator = ? OR  a.after_creator = ?)");
            params.add(creator);
            params.add(creator);
        }
        sql.append(" ORDER BY a.changed_at, a.id");
        List<DonationAuditRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                // JDBC 的索引是從 1 開始計算，所以是 i + 1
                statement.setObject(i + 1, params.get(i));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String snapshot = resultSet.getString("snapshot");
                    Donation current = mapCurrentDonation(resultSet);
                    Donation before = resultSet.getInt("before_recorded") != 0
                            ? mapAuditDonation(resultSet, "before_", getNullableInt(resultSet, "donation_id"),
                                    getNullableInt(resultSet, "audit_member_id"))
                            : parseBeforeDonation(snapshot);
                    Donation after = resultSet.getInt("after_recorded") != 0
                            ? mapAuditDonation(resultSet, "after_", getNullableInt(resultSet, "donation_id"),
                                    getNullableInt(resultSet, "audit_member_id"))
                            : parseAfterDonation(snapshot);
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
                            firstNonBlank(resultSet.getString("reason"), extractReason(snapshot)),
                            resultSet.getString("supplement_receipt_no")
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

    private void saveUpdateAudit(
            Connection connection,
            Donation before,
            Donation after,
            String changedBy,
            String memberName
    ) throws SQLException {
        String sql = """
                INSERT INTO donation_audits (
                    donation_id, member_id, action, changed_by, snapshot, member_name_at_change,
                    before_recorded, before_receipt_no, before_donate_date, before_amount,
                    before_donate_type, before_creator,
                    after_recorded, after_receipt_no, after_donate_date, after_amount,
                    after_donate_type, after_creator
                ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, after.getId());
            statement.setObject(2, after.getMemberId());
            statement.setString(3, AUDIT_ACTION_UPDATE);
            statement.setString(4, changedBy);
            statement.setString(5, "before=" + before + "\nafter=" + after);
            statement.setString(6, memberName);
            setAuditDonationFields(statement, 7, before);
            setAuditDonationFields(statement, 12, after);
            statement.executeUpdate();
        }
    }

    private void saveActionAudit(
            Connection connection,
            String action,
            Donation donation,
            String reason,
            String supplementReceiptNo,
            String changedBy,
            String memberName
    ) throws SQLException {
        String sql = """
                INSERT INTO donation_audits (
                    donation_id, member_id, action, changed_by, snapshot, member_name_at_change,
                    before_recorded, before_receipt_no, before_donate_date, before_amount,
                    before_donate_type, before_creator, reason, supplement_receipt_no
                ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, donation.getId());
            statement.setObject(2, donation.getMemberId());
            statement.setString(3, action);
            statement.setString(4, changedBy);
            statement.setString(5, donation + "\nreason=" + reason
                    + "\nsupplementReceiptNo=" + firstNonBlank(supplementReceiptNo, ""));
            statement.setString(6, memberName);
            setAuditDonationFields(statement, 7, donation);
            statement.setString(12, reason);
            statement.setString(13, supplementReceiptNo);
            statement.executeUpdate();
        }
    }

    private void setAuditDonationFields(
            PreparedStatement statement,
            int startIndex,
            Donation donation
    ) throws SQLException {
        statement.setString(startIndex, donation.getReceiptNo());
        statement.setString(startIndex + 1, donation.getDonateDate());
        statement.setObject(startIndex + 2, donation.getAmount());
        statement.setString(startIndex + 3, donation.getDonateType());
        statement.setString(startIndex + 4, donation.getCreator());
    }

    private Optional<Donation> findById(Connection connection, int id) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME
                + " WHERE id = ? AND COALESCE(is_deleted, 0) = 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    private String findMemberName(Connection connection, Integer memberId) throws SQLException {
        if (memberId == null) {
            return "";
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM light_members WHERE id = ?"
        )) {
            statement.setInt(1, memberId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("name") : "";
            }
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

    private void addAuditColumnIfMissing(
            Connection connection,
            String columnName,
            String definition
    ) throws SQLException {
        boolean exists = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(donation_audits)")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE donation_audits ADD COLUMN "
                        + columnName + " " + definition);
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
        donation.setUuid(resultSet.getString("uuid"));
        donation.setUpdatedAt(resultSet.getString("updated_at"));
        donation.setDeletedAt(resultSet.getString("deleted_at"));
        donation.setVersion((Integer) resultSet.getObject("version"));
        donation.setDeviceId(resultSet.getString("device_id"));
        donation.setSyncStatus(resultSet.getString("sync_status"));
        return donation;
    }

    private void setSyncFields(PreparedStatement statement, Donation donation) throws SQLException {
        statement.setString(14, donation.getUuid());
        statement.setString(15, donation.getUpdatedAt());
        statement.setString(16, donation.getDeletedAt());
        statement.setObject(17, donation.getVersion());
        statement.setString(18, donation.getDeviceId());
        statement.setString(19, donation.getSyncStatus());
    }

    private void applySyncDefaults(Donation donation) {
        if (donation.getUuid() == null || donation.getUuid().isBlank()) {
            donation.setUuid(UUID.randomUUID().toString());
        }
        donation.setUpdatedAt(tw.org.il.dongsheng.templeapp.util.Util.nowUtc());
        donation.setDeletedAt(null);
        if (donation.getVersion() == null || donation.getVersion() < 1) {
            donation.setVersion(1);
        }
        if (donation.getDeviceId() == null || donation.getDeviceId().isBlank()) {
            donation.setDeviceId("local");
        }
        if (donation.getSyncStatus() == null || donation.getSyncStatus().isBlank()) {
            donation.setSyncStatus("dirty");
        }
    }

    private void bumpSyncVersion(Donation donation) {
        donation.setVersion(donation.getVersion() == null ? 1 : donation.getVersion() + 1);
        donation.setUpdatedAt(tw.org.il.dongsheng.templeapp.util.Util.nowUtc());
        donation.setDeletedAt(null);
        if (donation.getDeviceId() == null || donation.getDeviceId().isBlank()) {
            donation.setDeviceId("local");
        }
        donation.setSyncStatus("dirty");
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

    private Donation mapAuditDonation(
            ResultSet resultSet,
            String prefix,
            Integer donationId,
            Integer memberId
    ) throws SQLException {
        Donation donation = new Donation();
        donation.setId(donationId);
        donation.setMemberId(memberId);
        donation.setReceiptNo(resultSet.getString(prefix + "receipt_no"));
        donation.setDonateDate(resultSet.getString(prefix + "donate_date"));
        donation.setAmount(getNullableInt(resultSet, prefix + "amount"));
        donation.setDonateType(resultSet.getString(prefix + "donate_type"));
        donation.setCreator(resultSet.getString(prefix + "creator"));
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

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Donation action reason cannot be blank.");
        }
    }

    private void requireSupplementReceiptNo(String supplementReceiptNo) {
        if (supplementReceiptNo == null || supplementReceiptNo.isBlank()) {
            throw new IllegalArgumentException("Supplement receipt number cannot be blank.");
        }
    }
}
