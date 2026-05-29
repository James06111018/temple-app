package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class HouseholdCountController {

    @FXML private TextField householdCountField;
    @FXML private TextField memberCountField;

    @FXML
    private void initialize() {
        loadCounts();
    }

    private void loadCounts() {
        try (Connection connection = SQLiteDatabaseManager.getInstance().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS light_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT,
                        phone TEXT,
                        city TEXT,
                        dist TEXT,
                        address TEXT
                    )
                    """);
            memberCountField.setText(String.valueOf(queryInt(statement, "SELECT COUNT(1) FROM light_members")));
            householdCountField.setText(String.valueOf(queryInt(statement, """
                    SELECT COUNT(1)
                    FROM (
                        SELECT COALESCE(NULLIF(TRIM(address), ''), printf('MEMBER:%d', id)) AS household_key
                        FROM light_members
                        GROUP BY household_key
                    )
                    """)));
        } catch (SQLException e) {
            AlertDialog.showError("總戶數", "讀取總戶數失敗：" + e.getMessage());
            householdCountField.setText("0");
            memberCountField.setText("0");
        }
    }

    private int queryInt(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }
}
