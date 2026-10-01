package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

/** One-time import of this Mac's reference data into the existing Neon tables. */
public final class ReferenceDataBootstrapTool {
    private ReferenceDataBootstrapTool() { }

    public static void main(String[] args) throws Exception {
        Properties config = SyncConfig.load();
        try (Connection local = SQLiteDatabaseManager.getInstance().getConnection();
             Connection remote = DriverManager.getConnection(
                     config.getProperty("sync.remote.jdbcUrl"),
                     config.getProperty("sync.remote.username"),
                     config.getProperty("sync.remote.password"))) {
            remote.setAutoCommit(false);
            try {
                int categories = copy(local, remote, "dictionary_categories", "id, code, name, type, enabled, sort_order, created_by, created_at, updated_by, updated_at",
                        "id", "code = EXCLUDED.code, name = EXCLUDED.name, type = EXCLUDED.type, enabled = EXCLUDED.enabled, sort_order = EXCLUDED.sort_order, created_by = EXCLUDED.created_by, created_at = EXCLUDED.created_at, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at");
                int items = copy(local, remote, "dictionary_items", "id, category_id, parent_item_id, code, name, description, amount, direction, default_amount, enabled, sort_order, source_table, source_id, created_by, created_at, updated_by, updated_at",
                        "id", "category_id = EXCLUDED.category_id, parent_item_id = EXCLUDED.parent_item_id, code = EXCLUDED.code, name = EXCLUDED.name, description = EXCLUDED.description, amount = EXCLUDED.amount, direction = EXCLUDED.direction, default_amount = EXCLUDED.default_amount, enabled = EXCLUDED.enabled, sort_order = EXCLUDED.sort_order, source_table = EXCLUDED.source_table, source_id = EXCLUDED.source_id, created_by = EXCLUDED.created_by, created_at = EXCLUDED.created_at, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at");
                int settings = copy(local, remote, "system_settings", "id, setting_group, setting_key, setting_value, updated_by, updated_at",
                        "id", "setting_group = EXCLUDED.setting_group, setting_key = EXCLUDED.setting_key, setting_value = EXCLUDED.setting_value, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at");
                remote.commit();
                System.out.printf("Imported %d categories, %d dictionary items, and %d system settings.%n", categories, items, settings);
            } catch (Exception ex) {
                remote.rollback();
                throw ex;
            }
        }
    }

    private static int copy(Connection local, Connection remote, String table, String columns, String conflictKey, String updates) throws Exception {
        String[] names = columns.split(", ");
        String placeholders = java.util.Arrays.stream(names)
                .map(name -> name.endsWith("_at") ? "?::timestamptz" : "?")
                .collect(java.util.stream.Collectors.joining(", "));
        String insert = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ") ON CONFLICT (" + conflictKey + ") DO UPDATE SET " + updates;
        int count = 0;
        try (PreparedStatement select = local.prepareStatement("SELECT " + columns + " FROM " + table);
             ResultSet rows = select.executeQuery();
             PreparedStatement upsert = remote.prepareStatement(insert)) {
            while (rows.next()) {
                for (int i = 0; i < names.length; i++) upsert.setObject(i + 1, rows.getObject(i + 1));
                upsert.addBatch();
                count++;
            }
            if (count > 0) upsert.executeBatch();
        }
        return count;
    }
}
