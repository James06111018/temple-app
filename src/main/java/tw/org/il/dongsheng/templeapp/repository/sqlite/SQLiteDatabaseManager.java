package tw.org.il.dongsheng.templeapp.repository.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteDatabaseManager {

    private static SQLiteDatabaseManager instance;
    private final String url;

    private SQLiteDatabaseManager(String databaseFilePath) {
        this.url = "jdbc:sqlite:" + databaseFilePath;
    }

    public static SQLiteDatabaseManager getInstance() {
        if (instance == null) {
            Path dbPath = getDatabasePath();
            instance = new SQLiteDatabaseManager(dbPath.toString());
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return DriverManager.getConnection(url);
    }

    private static Path getDatabasePath() {
        try {
            Path dbDir = getAppDataDir();
            Files.createDirectories(dbDir);
            return dbDir.resolve("temple.db").toAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("建立資料庫資料夾失敗", e);
        }
    }

    private static Path getAppDataDir() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return Paths.get(System.getenv("APPDATA"), "TempleApp");
        }

        if (os.contains("mac")) {
            return Paths.get(
                    System.getProperty("user.home"),
                    "Library",
                    "Application Support",
                    "TempleApp"
            );
        }

        return Paths.get(System.getProperty("user.home"), ".templeapp");
    }
}
