package tw.org.il.dongsheng.templeapp.sync;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NeonConnectionTest {

    @Test
    void canConnectToNeon() throws Exception {
        String jdbcUrl = System.getenv("TEMPLE_NEON_JDBC_URL");
        String username = System.getenv("TEMPLE_NEON_USERNAME");
        String password = System.getenv("TEMPLE_NEON_PASSWORD");

        Assumptions.assumeTrue(
                notBlank(jdbcUrl) && notBlank(username) && notBlank(password),
                "Neon env vars are not set."
        );

        Class.forName("org.postgresql.Driver");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select 1 as ok")) {
            assertTrue(resultSet.next());
            assertTrue(resultSet.getInt("ok") == 1);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
