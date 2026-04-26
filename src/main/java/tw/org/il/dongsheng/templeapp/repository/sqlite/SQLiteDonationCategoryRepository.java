package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.DonationCategory;
import tw.org.il.dongsheng.templeapp.repository.DonationCategoryRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteDonationCategoryRepository implements DonationCategoryRepository {

    private static final String TABLE_NAME = "donation_category";

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteDonationCategoryRepository(SQLiteDatabaseManager manager) {
        this.databaseManager = manager;
    }

    @Override
    public void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "code TEXT NOT NULL UNIQUE," +
                "name TEXT NOT NULL," +
                "type TEXT," +
                "amount INTEGER DEFAULT 0," +
                "is_enabled INTEGER DEFAULT 1," +
                "sort INTEGER DEFAULT 0," +
                "remark TEXT," +
                "is_system INTEGER DEFAULT 0," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public DonationCategory save(DonationCategory dc) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " (" +
                "code, name, type, amount, is_enabled, sort, remark" +
                ") VALUES(?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, dc.getCode());
            statement.setString(2, dc.getName());
            statement.setString(3, dc.getType());
            statement.setInt(4, dc.getAmount());
            statement.setInt(5, dc.isEnabled() ? 1 : 0);
            statement.setInt(6, dc.getSort());
            statement.setString(7, dc.getRemark());

            statement.executeUpdate();
        }
        return dc;
    }

    @Override
    public boolean update(DonationCategory dc) throws SQLException {
        String sql = "UPDATE " + TABLE_NAME + " SET " +
                "name = ?, type = ?, amount = ?, is_enabled = ?, sort = ?, remark = ? " +
                "WHERE code = ? AND is_system = 0";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dc.getName());
            statement.setString(2, dc.getType());
            statement.setInt(3, dc.getAmount());
            statement.setInt(4, dc.isEnabled() ? 1 : 0);
            statement.setInt(5, dc.getSort());
            statement.setString(6, dc.getRemark());
            statement.setString(7, dc.getCode());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public List<DonationCategory> findAll() throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " ORDER BY sort";
        List<DonationCategory> list = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                DonationCategory d = new DonationCategory(
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("type"),
                        resultSet.getInt("amount"),
                        resultSet.getInt("is_enabled") == 1,
                        resultSet.getInt("sort"),
                        resultSet.getString("remark")
                );
                list.add(d);
            }
        }

        return list;
    }

    @Override
    public boolean disable(String code) throws SQLException {
        String sql = "UPDATE " + TABLE_NAME + " " +
                "SET is_enabled = 0 " +
                "WHERE code = ? AND is_system = 0";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, code);
            return statement.executeUpdate() > 0;
        }

    }
}
