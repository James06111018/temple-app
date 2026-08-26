package tw.org.il.dongsheng.templeapp.sync;

import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public final class NeonConnectionTestTool {
    private NeonConnectionTestTool() {
    }

    public static void main(String[] args) throws Exception {
        Properties syncConfig = SyncConfig.load();
        String jdbcUrl = firstNonBlank(
                syncConfig.getProperty("sync.remote.jdbcUrl"),
                System.getenv("TEMPLE_NEON_JDBC_URL")
        );
        String username = firstNonBlank(
                syncConfig.getProperty("sync.remote.username"),
                System.getenv("TEMPLE_NEON_USERNAME")
        );
        String password = firstNonBlank(
                syncConfig.getProperty("sync.remote.password"),
                System.getenv("TEMPLE_NEON_PASSWORD")
        );

        if (isBlank(jdbcUrl) || isBlank(username) || isBlank(password)) {
            throw new IllegalStateException(
                    "Please set TEMPLE_NEON_JDBC_URL, TEMPLE_NEON_USERNAME, and TEMPLE_NEON_PASSWORD.");
        }

        Class.forName("org.postgresql.Driver");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select now() as server_time, version() as server_version")) {

            if (resultSet.next()) {
                System.out.println("Neon connection OK");
                System.out.println("server_time=" + resultSet.getString("server_time"));
                System.out.println("server_version=" + resultSet.getString("server_version"));
            } else {
                System.out.println("Neon connection OK, but no result returned.");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return isBlank(primary) ? fallback : primary;
    }
}
