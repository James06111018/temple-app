package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.MeritCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteMeritCategoryRepository {
    private final SQLiteDatabaseManager databaseManager;

    public SQLiteMeritCategoryRepository() {
        this(SQLiteDatabaseManager.getInstance());
    }

    public SQLiteMeritCategoryRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        initializeSchema();
    }

    public List<MeritCategory> findAll(boolean includeDeleted) throws SQLException {
        String sql = "SELECT id, code, name, is_delete FROM merit_categories "
                + (includeDeleted ? "" : "WHERE is_delete = 0 ")
                + "ORDER BY is_delete, code COLLATE NOCASE";
        List<MeritCategory> categories = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                categories.add(new MeritCategory(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getInt("is_delete") == 1
                ));
            }
        }
        return categories;
    }

    public void save(MeritCategory category, String changedBy) throws SQLException {
        if (category.getId() == null) {
            insert(category, changedBy);
        } else {
            update(category, changedBy);
        }
    }

    public void softDelete(long id, String changedBy) throws SQLException {
        changeDeletedState(id, true, "DELETE", changedBy);
    }

    public void restore(long id, String changedBy) throws SQLException {
        changeDeletedState(id, false, "RESTORE", changedBy);
    }

    public void replaceAll(List<MeritCategory> categories) throws SQLException {
        initializeSchema();
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteAudit = connection.prepareStatement("DELETE FROM merit_category_audits");
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM merit_categories");
                 PreparedStatement insert = connection.prepareStatement(
                         """
                         INSERT INTO merit_categories (id, code, name, is_delete, created_by, created_at, updated_by, updated_at)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                         ON CONFLICT(code) DO UPDATE SET
                             name = excluded.name,
                             is_delete = excluded.is_delete,
                             created_by = excluded.created_by,
                             created_at = excluded.created_at,
                             updated_by = excluded.updated_by,
                             updated_at = excluded.updated_at
                         """)) {
                connection.createStatement().execute("PRAGMA foreign_keys = OFF");
                deleteAudit.executeUpdate();
                delete.executeUpdate();
                if (categories != null) {
                    for (MeritCategory category : categories) {
                        insert.setObject(1, category.getId());
                        insert.setString(2, category.getCode());
                        insert.setString(3, category.getName());
                        insert.setInt(4, category.isDeleted() ? 1 : 0);
                        insert.setString(5, "SYNC");
                        insert.setString(6, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        insert.setString(7, null);
                        insert.setString(8, null);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.createStatement().execute("PRAGMA foreign_keys = ON");
                connection.setAutoCommit(true);
            }
        }
    }

    private void initializeSchema() {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS merit_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        name TEXT NOT NULL,
                        is_delete INTEGER NOT NULL DEFAULT 0,
                        created_by TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by TEXT,
                        updated_at TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS merit_category_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        category_id INTEGER,
                        action TEXT NOT NULL,
                        code TEXT,
                        name TEXT,
                        changed_by TEXT NOT NULL,
                        changed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            seedDefaults(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("建立功德箱分類資料表失敗", e);
        }
    }

    private void seedDefaults(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM merit_categories")) {
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                return;
            }
        }
        insertSeed(connection, "A", "功德箱");
        insertSeed(connection, "B", "金炮燭");
    }

    private void insertSeed(Connection connection, String code, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO merit_categories(code, name, created_by) VALUES (?, ?, 'SYSTEM')")) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private void insert(MeritCategory category, String changedBy) throws SQLException {
        String sql = "INSERT INTO merit_categories(code, name, created_by) VALUES (?, ?, ?)";
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, category.getCode());
                statement.setString(2, category.getName());
                statement.setString(3, changedBy);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        category.setId(keys.getLong(1));
                    }
                }
                writeAudit(connection, category, "ADD", changedBy);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void update(MeritCategory category, String changedBy) throws SQLException {
        String sql = "UPDATE merit_categories SET code = ?, name = ?, updated_by = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_delete = 0";
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, category.getCode());
                statement.setString(2, category.getName());
                statement.setString(3, changedBy);
                statement.setLong(4, category.getId());
                statement.executeUpdate();
                writeAudit(connection, category, "UPDATE", changedBy);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void changeDeletedState(long id, boolean deleted, String action, String changedBy) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MeritCategory category = findById(connection, id);
                if (category == null) {
                    throw new SQLException("找不到分類資料");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE merit_categories SET is_delete = ?, updated_by = ?, "
                                + "updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                    statement.setInt(1, deleted ? 1 : 0);
                    statement.setString(2, changedBy);
                    statement.setLong(3, id);
                    statement.executeUpdate();
                }
                category.setDeleted(deleted);
                writeAudit(connection, category, action, changedBy);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private MeritCategory findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, code, name, is_delete FROM merit_categories WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new MeritCategory(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getInt("is_delete") == 1
                );
            }
        }
    }

    private void writeAudit(Connection connection, MeritCategory category, String action, String changedBy)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO merit_category_audits(category_id, action, code, name, changed_by) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            if (category.getId() == null) {
                statement.setNull(1, java.sql.Types.INTEGER);
            } else {
                statement.setLong(1, category.getId());
            }
            statement.setString(2, action);
            statement.setString(3, category.getCode());
            statement.setString(4, category.getName());
            statement.setString(5, changedBy);
            statement.executeUpdate();
        }
    }
}
