package tw.org.il.dongsheng.templeapp.repository.sqlite;

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
            instance = new SQLiteDatabaseManager("temple.db");
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
}
