package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.MeritBoxOpening;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SQLiteMeritBoxOpeningRepository {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SQLiteDatabaseManager databaseManager;

    public SQLiteMeritBoxOpeningRepository() {
        this(SQLiteDatabaseManager.getInstance());
    }

    public SQLiteMeritBoxOpeningRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        initializeSchema();
    }

    public MeritBoxOpening save(MeritBoxOpening opening) throws SQLException {
        String sql = """
                INSERT INTO merit_box_openings (
                    opening_date, serial_no, amount, opener, note,
                    category_code, category_name, created_by, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        LocalDateTime createdAt = opening.createdAt() == null ? LocalDateTime.now() : opening.createdAt();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, opening.openingDate().toString());
            statement.setString(2, opening.serialNo());
            statement.setLong(3, opening.amount());
            statement.setString(4, opening.opener());
            statement.setString(5, opening.note());
            statement.setString(6, opening.categoryCode());
            statement.setString(7, opening.categoryName());
            statement.setString(8, opening.createdBy());
            statement.setString(9, createdAt.format(DATE_TIME_FORMATTER));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                Long id = keys.next() ? keys.getLong(1) : null;
                return new MeritBoxOpening(id, opening.openingDate(), opening.serialNo(), opening.amount(),
                        opening.opener(), opening.note(), opening.categoryCode(), opening.categoryName(),
                        opening.createdBy(), createdAt);
            }
        }
    }

    public List<MeritBoxOpening> search(String categoryCode, LocalDate startDate, LocalDate endDate) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT id, opening_date, serial_no, amount, opener, note,
                       category_code, category_name, created_by, created_at
                  FROM merit_box_openings
                 WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (categoryCode != null && !categoryCode.isBlank()) {
            sql.append(" AND category_code = ?");
            parameters.add(categoryCode);
        }
        if (startDate != null) {
            sql.append(" AND opening_date >= ?");
            parameters.add(startDate.toString());
        }
        if (endDate != null) {
            sql.append(" AND opening_date <= ?");
            parameters.add(endDate.toString());
        }
        sql.append(" ORDER BY opening_date, id");

        List<MeritBoxOpening> openings = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    openings.add(map(resultSet));
                }
            }
        }
        return openings;
    }

    public String nextSerialNo() throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(serial_no AS INTEGER)), 0) + 1 FROM merit_box_openings";
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? String.format("%06d", resultSet.getLong(1)) : "000001";
        }
    }

    private MeritBoxOpening map(ResultSet resultSet) throws SQLException {
        String createdAt = resultSet.getString("created_at");
        return new MeritBoxOpening(
                resultSet.getLong("id"),
                LocalDate.parse(resultSet.getString("opening_date")),
                resultSet.getString("serial_no"),
                resultSet.getLong("amount"),
                resultSet.getString("opener"),
                resultSet.getString("note"),
                resultSet.getString("category_code"),
                resultSet.getString("category_name"),
                resultSet.getString("created_by"),
                createdAt == null || createdAt.isBlank() ? null : LocalDateTime.parse(createdAt, DATE_TIME_FORMATTER)
        );
    }

    private void initializeSchema() {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS merit_box_openings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        opening_date TEXT NOT NULL,
                        serial_no TEXT NOT NULL,
                        amount INTEGER NOT NULL CHECK (amount > 0),
                        opener TEXT NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        category_code TEXT NOT NULL,
                        category_name TEXT NOT NULL,
                        created_by TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_merit_openings_date ON merit_box_openings(opening_date)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_merit_openings_category ON merit_box_openings(category_code)");
        } catch (SQLException e) {
            throw new IllegalStateException("建立功德箱開箱紀錄資料表失敗", e);
        }
    }
}
