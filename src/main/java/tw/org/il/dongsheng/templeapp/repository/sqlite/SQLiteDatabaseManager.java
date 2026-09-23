package tw.org.il.dongsheng.templeapp.repository.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SQLiteDatabaseManager {

    private static SQLiteDatabaseManager instance;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final String url;
    private final Path databasePath;

    private SQLiteDatabaseManager(String databaseFilePath) {
        this.databasePath = Paths.get(databaseFilePath).toAbsolutePath();
        this.url = "jdbc:sqlite:" + this.databasePath;
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
        Connection connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public Path createBackup() throws SQLException {
        try {
            Path backupDir = databasePath.getParent().resolve("backups");
            Files.createDirectories(backupDir);
            Path backupPath = backupDir.resolve(
                    "temple-before-cloud-download-" + LocalDateTime.now().format(BACKUP_TIMESTAMP) + ".db"
            );
            String escapedPath = backupPath.toString().replace("'", "''");
            try (Connection connection = getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(FULL)");
                statement.execute("VACUUM INTO '" + escapedPath + "'");
            }
            return backupPath;
        } catch (java.io.IOException ex) {
            throw new SQLException("建立本機資料庫備份失敗", ex);
        }
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
