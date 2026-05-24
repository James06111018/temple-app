package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.model.LightType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteDictionaryRepository {
    public static final String TYPE_LIGHT = "LIGHT";
    public static final String TYPE_DONATION_LIGHT = "DONATION_LIGHT";
    public static final String TYPE_DONATION_GHOST = "DONATION_GHOST";
    private static final int LIGHT_ID_OFFSET = 10000;

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteDictionaryRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createTable() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS dictionary_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT NOT NULL UNIQUE,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        sort_order INTEGER,
                        created_by TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_by TEXT,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS dictionary_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        category_id INTEGER NOT NULL,
                        code TEXT,
                        name TEXT NOT NULL,
                        description TEXT,
                        amount INTEGER DEFAULT 0,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        sort_order INTEGER,
                        source_table TEXT,
                        source_id INTEGER,
                        created_by TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_by TEXT,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(category_id, code),
                        FOREIGN KEY(category_id) REFERENCES dictionary_categories(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS dictionary_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        target_table TEXT NOT NULL,
                        target_id INTEGER,
                        action TEXT NOT NULL,
                        old_value TEXT,
                        new_value TEXT,
                        changed_by TEXT,
                        changed_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_dictionary_categories_type ON dictionary_categories(type)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_dictionary_items_category ON dictionary_items(category_id)");
        }
    }

    public void migrateFromLegacy() throws SQLException {
        createTable();
        seedCategory("LIGHT", "點燈", TYPE_LIGHT, 1);
        seedCategory("DONATION_LIGHT", "信眾點燈款項", TYPE_DONATION_LIGHT, 2);
        seedCategory("DONATION_GHOST", "中元普渡款項", TYPE_DONATION_GHOST, 3);
        migrateLightTypes();
        migrateDonationCategories();
    }

    public List<DictionaryItem> findEnabledItemsByType(String type) throws SQLException {
        createTable();
        String sql = """
                SELECT i.id, c.code AS category_code, i.code, i.name, i.description, i.amount, i.enabled, i.sort_order
                FROM dictionary_items i
                JOIN dictionary_categories c ON c.id = i.category_id
                WHERE c.type = ?
                  AND c.enabled = 1
                  AND i.enabled = 1
                ORDER BY COALESCE(i.sort_order, i.id), i.id
                """;
        List<DictionaryItem> items = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(mapItem(resultSet));
                }
            }
        }
        return items;
    }

    public List<DictionaryItem> findAllItems() throws SQLException {
        createTable();
        String sql = """
                SELECT i.id, c.code AS category_code, i.code, i.name, i.description, i.amount, i.enabled, i.sort_order
                FROM dictionary_items i
                JOIN dictionary_categories c ON c.id = i.category_id
                ORDER BY c.sort_order, COALESCE(i.sort_order, i.id), i.id
                """;
        List<DictionaryItem> items = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                items.add(mapItem(resultSet));
            }
        }
        return items;
    }

    public DictionaryItem saveItem(String type, DictionaryItem item, String changedBy) throws SQLException {
        createTable();
        int categoryId = findCategoryIdByType(type);
        String sql = """
                INSERT INTO dictionary_items (category_id, code, name, description, amount, enabled, sort_order, created_by, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, categoryId);
            statement.setString(2, item.getCode());
            statement.setString(3, item.getName());
            statement.setString(4, item.getDescription());
            statement.setObject(5, item.getAmount());
            statement.setInt(6, item.isEnabled() ? 1 : 0);
            statement.setObject(7, item.getSortOrder());
            statement.setString(8, changedBy);
            statement.setString(9, changedBy);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setId(keys.getInt(1));
                }
            }
        }
        saveAudit("dictionary_items", item.getId(), "CREATE", null, item.getName(), changedBy);
        return item;
    }

    public boolean updateItem(DictionaryItem item, String changedBy) throws SQLException {
        createTable();
        String sql = """
                UPDATE dictionary_items
                SET code = ?, name = ?, description = ?, amount = ?, enabled = ?, sort_order = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        boolean updated;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getCode());
            statement.setString(2, item.getName());
            statement.setString(3, item.getDescription());
            statement.setObject(4, item.getAmount());
            statement.setInt(5, item.isEnabled() ? 1 : 0);
            statement.setObject(6, item.getSortOrder());
            statement.setString(7, changedBy);
            statement.setObject(8, item.getId());
            updated = statement.executeUpdate() > 0;
        }
        if (updated) {
            saveAudit("dictionary_items", item.getId(), "UPDATE", null, item.getName(), changedBy);
        }
        return updated;
    }

    public LightType toLightType(DictionaryItem item) {
        return new LightType(item.getId(), item.getCode(), item.getName(), item.isEnabled(), item.getSortOrder());
    }

    private void seedCategory(String code, String name, String type, int sortOrder) throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO dictionary_categories (code, name, type, enabled, sort_order, created_by, updated_by, updated_at)
                VALUES (?, ?, ?, 1, ?, 'system', 'system', CURRENT_TIMESTAMP)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setString(3, type);
            statement.setInt(4, sortOrder);
            statement.executeUpdate();
        }
    }

    private void migrateLightTypes() throws SQLException {
        if (!tableExists("light_types")) {
            seedDefaultLightItems();
            return;
        }
        int categoryId = findCategoryIdByType(TYPE_LIGHT);
        String sql = """
                INSERT OR IGNORE INTO dictionary_items
                    (id, category_id, code, name, amount, enabled, sort_order, source_table, source_id, created_by, updated_by, updated_at)
                SELECT id + ?, ?, code, name, 0, enabled, sort_order, 'light_types', id, 'system', 'system', CURRENT_TIMESTAMP
                FROM light_types
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, LIGHT_ID_OFFSET);
            statement.setInt(2, categoryId);
            statement.executeUpdate();
        }
        if (tableExists("household_light_records")) {
            try (Connection connection = databaseManager.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("UPDATE household_light_records SET light_type_id = light_type_id + " + LIGHT_ID_OFFSET + " WHERE light_type_id < " + LIGHT_ID_OFFSET);
            }
        }
        seedDefaultLightItems();
        dropTable("light_types");
    }

    private void migrateDonationCategories() throws SQLException {
        if (!tableExists("donation_category")) {
            return;
        }
        int categoryId = findCategoryIdByType(TYPE_DONATION_LIGHT);
        String sql = """
                INSERT OR IGNORE INTO dictionary_items
                    (id, category_id, code, name, description, amount, enabled, sort_order, source_table, source_id, created_by, updated_by, updated_at)
                SELECT id, ?, code, name, remark, amount, is_enabled, sort, 'donation_category', id, 'system', 'system', CURRENT_TIMESTAMP
                FROM donation_category
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            statement.executeUpdate();
        }
        dropTable("donation_category");
    }

    private void seedDefaultLightItems() throws SQLException {
        if (hasItems(TYPE_LIGHT)) {
            return;
        }
        int categoryId = findCategoryIdByType(TYPE_LIGHT);
        String sql = """
                INSERT OR IGNORE INTO dictionary_items
                    (id, category_id, code, name, amount, enabled, sort_order, source_table, source_id, created_by, updated_by, updated_at)
                VALUES (?, ?, ?, ?, 0, 1, ?, 'default', ?, 'system', 'system', CURRENT_TIMESTAMP)
                """;
        String[] names = {"安太歲", "光明燈", "虎爺燈", "媽祖燈", "媽祖內殿燈", "宮燈", "註生內殿燈", "福德內殿燈", "三界公燈"};
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < names.length; i++) {
                int sourceId = i + 1;
                statement.setInt(1, LIGHT_ID_OFFSET + sourceId);
                statement.setInt(2, categoryId);
                statement.setString(3, String.valueOf(sourceId));
                statement.setString(4, names[i]);
                statement.setInt(5, sourceId);
                statement.setInt(6, sourceId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean hasItems(String type) throws SQLException {
        String sql = """
                SELECT COUNT(1)
                FROM dictionary_items i
                JOIN dictionary_categories c ON c.id = i.category_id
                WHERE c.type = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private int findCategoryIdByType(String type) throws SQLException {
        String sql = "SELECT id FROM dictionary_categories WHERE type = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }
        throw new SQLException("Dictionary category not found: " + type);
    }

    private boolean tableExists(String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void dropTable(String tableName) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    private void saveAudit(String table, Integer targetId, String action, String oldValue, String newValue, String changedBy) throws SQLException {
        String sql = """
                INSERT INTO dictionary_audits (target_table, target_id, action, old_value, new_value, changed_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setObject(2, targetId);
            statement.setString(3, action);
            statement.setString(4, oldValue);
            statement.setString(5, newValue);
            statement.setString(6, changedBy);
            statement.executeUpdate();
        }
    }

    private DictionaryItem mapItem(ResultSet resultSet) throws SQLException {
        return new DictionaryItem(
                resultSet.getInt("id"),
                resultSet.getString("category_code"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                (Integer) resultSet.getObject("amount"),
                resultSet.getInt("enabled") == 1,
                (Integer) resultSet.getObject("sort_order")
        );
    }
}
