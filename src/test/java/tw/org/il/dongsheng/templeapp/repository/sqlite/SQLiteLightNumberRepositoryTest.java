package tw.org.il.dongsheng.templeapp.repository.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SQLiteLightNumberRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesLegacyChineseManagementTypes() {
        assertEquals("TAI_SUI", SQLiteLightNumberRepository.normalizeManagementType("安太歲"));
        assertEquals("LIGHT", SQLiteLightNumberRepository.normalizeManagementType("點燈"));
        assertEquals("NUMBER", SQLiteLightNumberRepository.normalizeManagementType("普渡編號"));
        assertEquals("LIGHT", SQLiteLightNumberRepository.normalizeManagementType(" LIGHT "));
    }

    @Test
    void migratesLegacyRowsAndMergesCanonicalDuplicates() throws Exception {
        Constructor<SQLiteDatabaseManager> constructor =
                SQLiteDatabaseManager.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        SQLiteDatabaseManager databaseManager = constructor.newInstance(tempDir.resolve("test.db").toString());
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE light_members (id INTEGER PRIMARY KEY)");
        }
        SQLiteLightNumberRepository repository = new SQLiteLightNumberRepository(databaseManager);
        repository.createTable();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO light_numbers (management_type, light_type, serial_number, status)
                    VALUES ('TAI_SUI', '太', 1, 'N')
                    """);
            statement.executeUpdate("""
                    INSERT INTO light_numbers (management_type, light_type, serial_number, status, principal_name)
                    VALUES ('安太歲', '太', 1, 'A', '雲端信眾')
                    """);
            statement.executeUpdate("""
                    INSERT INTO light_numbers (management_type, light_type, serial_number, status)
                    VALUES ('點燈', '光', 2, 'N'), ('普渡編號', '普', 3, 'N')
                    """);
        }

        repository.createTable();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT management_type, light_type, serial_number, status, principal_name
                     FROM light_numbers
                     ORDER BY management_type
                     """)) {
            int count = 0;
            while (resultSet.next()) {
                count++;
                String type = resultSet.getString("management_type");
                if ("TAI_SUI".equals(type)) {
                    assertEquals("A", resultSet.getString("status"));
                    assertEquals("雲端信眾", resultSet.getString("principal_name"));
                }
            }
            assertEquals(3, count);
        }
    }
}
