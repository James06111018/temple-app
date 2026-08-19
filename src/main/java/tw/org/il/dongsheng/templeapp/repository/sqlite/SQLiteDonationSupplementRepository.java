package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.DonationSupplement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQLiteDonationSupplementRepository {
    public static final String SOURCE_HOUSEHOLD_LIGHT = "HOUSEHOLD_LIGHT";

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteDonationSupplementRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createTable() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS donation_supplements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        donation_id INTEGER NOT NULL UNIQUE,
                        supplement_date TEXT NOT NULL,
                        supplement_no TEXT NOT NULL,
                        source_type TEXT NOT NULL,
                        created_by TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_by TEXT,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(donation_id) REFERENCES donations(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS donation_supplement_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        supplement_id INTEGER,
                        donation_id INTEGER,
                        action TEXT NOT NULL,
                        changed_by TEXT,
                        changed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        snapshot TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_donation_supplements_date ON donation_supplements(supplement_date)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_donation_supplements_no ON donation_supplements(supplement_no)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_donation_supplements_deleted ON donation_supplements(is_deleted)");
        }
    }

    public DonationSupplement save(DonationSupplement supplement, String changedBy) throws SQLException {
        if (supplement.getDonationId() == null) {
            throw new IllegalArgumentException("Supplement donation id cannot be null.");
        }
        createTable();
        String sql = """
                INSERT INTO donation_supplements (
                    donation_id, supplement_date, supplement_no, source_type,
                    created_by, updated_by, updated_at, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 0)
                ON CONFLICT(donation_id) DO UPDATE SET
                    supplement_date = excluded.supplement_date,
                    supplement_no = excluded.supplement_no,
                    source_type = excluded.source_type,
                    updated_by = excluded.updated_by,
                    updated_at = CURRENT_TIMESTAMP,
                    is_deleted = 0
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, supplement.getDonationId());
            statement.setString(2, supplement.getSupplementDate());
            statement.setString(3, supplement.getSupplementNo());
            statement.setString(4, supplement.getSourceType());
            statement.setString(5, changedBy);
            statement.setString(6, changedBy);
            statement.executeUpdate();
        }

        supplement.setId(findIdByDonationId(supplement.getDonationId()));
        supplement.setCreatedBy(changedBy);
        supplement.setUpdatedBy(changedBy);
        saveAudit(supplement, "UPSERT", changedBy);
        return supplement;
    }

    public List<DonationSupplement> findByDateRange(LocalDate startDate, LocalDate endDate, String creator)
            throws SQLException {
        createTable();
        StringBuilder sql = new StringBuilder("""
                SELECT id, donation_id, supplement_date, supplement_no, source_type,
                       created_by, created_at, updated_by, updated_at, is_deleted
                FROM donation_supplements
                WHERE COALESCE(is_deleted, 0) = 0
                  AND supplement_date BETWEEN ? AND ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(startDate);
        params.add(endDate);
        if (creator != null && !creator.trim().isEmpty()) {
            sql.append(" AND created_by = ? OR updated_by = ?");
            params.add(creator);
            params.add(creator);
        }
        sql.append(" ORDER BY supplement_date, supplement_no, id");

        List<DonationSupplement> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                // JDBC 的索引是從 1 開始計算，所以是 i + 1
                statement.setObject(i + 1, params.get(i));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(mapRow(resultSet));
                }
            }
        }
        return result;
    }

    public boolean deleteById(int id, String changedBy) throws SQLException {
        createTable();
        String sql = """
                UPDATE donation_supplements
                SET is_deleted = 1,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND COALESCE(is_deleted, 0) = 0
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, changedBy);
            statement.setInt(2, id);
            boolean deleted = statement.executeUpdate() > 0;
            if (deleted) {
                DonationSupplement supplement = new DonationSupplement();
                supplement.setId(id);
                saveAudit(supplement, "DELETE", changedBy);
            }
            return deleted;
        }
    }

    private Integer findIdByDonationId(int donationId) throws SQLException {
        String sql = "SELECT id FROM donation_supplements WHERE donation_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, donationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("id") : null;
            }
        }
    }

    public Map<Integer, String> findSupplementNosByDonationIds(List<Integer> donationIds) throws SQLException {
        // 防呆：如果傳入的 ID 清單是空的，直接回傳空 Map，不走資料庫
        if (donationIds == null || donationIds.isEmpty()) {
            return new HashMap<>();
        }

        // 1. 動態組裝 IN (?, ?, ?) 語法
        StringBuilder sql = new StringBuilder("SELECT donation_id, supplement_no FROM donation_supplements WHERE donation_id IN (");
        for (int i = 0; i < donationIds.size(); i++) {
            sql.append("?");
            if (i < donationIds.size() - 1) {
                sql.append(", ");
            }
        }
        sql.append(")");

        Map<Integer, String> resultMap = new HashMap<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            // 2. 依序綁定多筆 donationId 參數
            for (int i = 0; i < donationIds.size(); i++) {
                statement.setInt(i + 1, donationIds.get(i));
            }

            // 3. 執行查詢並將結果包裝成 Map
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int donationId = resultSet.getInt("donation_id");
                    String supplementNo = resultSet.getString("supplement_no");
                    resultMap.put(donationId, supplementNo);
                }
            }
        }

        return resultMap;
    }

    private DonationSupplement mapRow(ResultSet resultSet) throws SQLException {
        return new DonationSupplement(
                resultSet.getInt("id"),
                resultSet.getInt("donation_id"),
                resultSet.getString("supplement_date"),
                resultSet.getString("supplement_no"),
                resultSet.getString("source_type"),
                resultSet.getString("created_by"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_by"),
                resultSet.getString("updated_at"),
                resultSet.getInt("is_deleted") != 0
        );
    }

    private void saveAudit(DonationSupplement supplement, String action, String changedBy)
            throws SQLException {
        String sql = """
                INSERT INTO donation_supplement_audits (
                    supplement_id, donation_id, action, changed_by, snapshot
                ) VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, supplement.getId());
            statement.setObject(2, supplement.getDonationId());
            statement.setString(3, action);
            statement.setString(4, changedBy);
            statement.setString(5, "date=" + supplement.getSupplementDate()
                    + ", no=" + supplement.getSupplementNo()
                    + ", source=" + supplement.getSourceType());
            statement.executeUpdate();
        }
    }
}
