package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.AppUser;
import tw.org.il.dongsheng.templeapp.model.AppRole;
import tw.org.il.dongsheng.templeapp.model.AppFunction;
import tw.org.il.dongsheng.templeapp.model.LoginRecord;
import tw.org.il.dongsheng.templeapp.model.UserAuditRecord;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SQLiteAuthRepository {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_VIEWER = "VIEWER";

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteAuthRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createTables() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS app_roles (
                        role_code TEXT PRIMARY KEY,
                        role_name TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS app_functions (
                        function_code TEXT PRIMARY KEY,
                        function_name TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS role_functions (
                        role_code TEXT NOT NULL,
                        function_code TEXT NOT NULL,
                        PRIMARY KEY(role_code, function_code),
                        FOREIGN KEY(role_code) REFERENCES app_roles(role_code),
                        FOREIGN KEY(function_code) REFERENCES app_functions(function_code)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS app_users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        display_name TEXT NOT NULL,
                        password_hash TEXT NOT NULL,
                        role_code TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        created_by TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_by TEXT,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY(role_code) REFERENCES app_roles(role_code)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS app_user_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER,
                        action TEXT NOT NULL,
                        changed_by TEXT,
                        changed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        snapshot TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS login_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER,
                        username TEXT,
                        display_name TEXT,
                        role_code TEXT,
                        computer_name TEXT,
                        login_date TEXT,
                        login_time TEXT,
                        logout_date TEXT,
                        logout_time TEXT,
                        status TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        seedDefaults();
    }

    private void seedDefaults() throws SQLException {
        upsertRole(ROLE_ADMIN, "admin");
        upsertRole(ROLE_MANAGER, "管理者");
        upsertRole(ROLE_USER, "使用者");
        upsertRole(ROLE_VIEWER, "檢視者");
        upsertFunction("SYSTEM_ADMIN", "系統管理", true);
        upsertFunction("USER_MANAGEMENT", "使用者管理", true);
        upsertFunction("ROLE_MANAGEMENT", "角色管理", true);
        upsertFunction("FUNCTION_MANAGEMENT", "功能管理", true);
        upsertFunction("USER_AUDIT_QUERY", "使用者異動紀錄", true);
        grantRoleFunctions(ROLE_ADMIN, findAllFunctionCodes());
        grantRoleFunctions(ROLE_MANAGER, Set.of("SYSTEM_ADMIN", "USER_MANAGEMENT", "USER_AUDIT_QUERY"));

    }

    private void upsertRole(String code, String name) throws SQLException {
        String sql = """
                INSERT INTO app_roles (role_code, role_name)
                VALUES (?, ?)
                ON CONFLICT(role_code) DO UPDATE SET role_name = excluded.role_name
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    public void saveRole(AppRole role) throws SQLException {
        upsertRole(role.getRoleCode(), role.getRoleName());
    }

    private void upsertFunction(String code, String name, boolean enabled) throws SQLException {
        String sql = """
                INSERT INTO app_functions (function_code, function_name, enabled)
                VALUES (?, ?, ?)
                ON CONFLICT(function_code) DO UPDATE SET function_name = excluded.function_name, enabled = excluded.enabled
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setInt(3, enabled ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public void saveFunction(AppFunction function) throws SQLException {
        upsertFunction(function.getFunctionCode(), function.getFunctionName(), function.isEnabled());
    }

    public Optional<AppUser> authenticate(String username, String password) throws SQLException {
        String sql = "SELECT id, username, display_name, role_code, enabled FROM app_users WHERE username = ? AND password_hash = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, hashPassword(password));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt("enabled") == 1) {
                    return Optional.of(mapUser(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public void updatePassword(String username, String newPassword, String changedBy) throws SQLException {
        String sql = """
                UPDATE app_users
                SET password_hash = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE username = ? AND enabled = 1
                """;
        Integer userId;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hashPassword(newPassword));
            statement.setString(2, changedBy);
            statement.setString(3, username);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("找不到啟用中的本機使用者：" + username);
            }
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM app_users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                userId = resultSet.next() ? resultSet.getInt("id") : null;
            }
        }
        saveUserAudit(userId, "PASSWORD_CHANGE", changedBy, "使用者變更自己的密碼");
    }

    public Set<String> findFunctionCodesByRole(String roleCode) throws SQLException {
        String sql = """
                SELECT f.function_code
                FROM role_functions rf
                JOIN app_functions f ON f.function_code = rf.function_code
                WHERE rf.role_code = ? AND f.enabled = 1
                """;
        Set<String> codes = new HashSet<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roleCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    codes.add(resultSet.getString("function_code"));
                }
            }
        }
        return codes;
    }

    public List<AppRole> findAllRoles() throws SQLException {
        String sql = "SELECT role_code, role_name FROM app_roles ORDER BY role_code";
        List<AppRole> roles = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                roles.add(new AppRole(resultSet.getString("role_code"), resultSet.getString("role_name")));
            }
        }
        return roles;
    }

    public List<AppFunction> findAllFunctions() throws SQLException {
        String sql = "SELECT function_code, function_name, enabled FROM app_functions ORDER BY function_code";
        List<AppFunction> functions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                functions.add(new AppFunction(
                        resultSet.getString("function_code"),
                        resultSet.getString("function_name"),
                        resultSet.getInt("enabled") == 1
                ));
            }
        }
        return functions;
    }

    private Set<String> findAllFunctionCodes() throws SQLException {
        Set<String> codes = new HashSet<>();
        for (AppFunction function : findAllFunctions()) {
            codes.add(function.getFunctionCode());
        }
        return codes;
    }

    public void grantRoleFunctions(String roleCode, Set<String> functionCodes) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM role_functions WHERE role_code = ?");
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO role_functions (role_code, function_code) VALUES (?, ?)")) {
                delete.setString(1, roleCode);
                delete.executeUpdate();
                for (String functionCode : functionCodes) {
                    insert.setString(1, roleCode);
                    insert.setString(2, functionCode);
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public SQLiteDatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public List<AppUser> findAllUsers() throws SQLException {
        String sql = "SELECT id, username, display_name, role_code, enabled FROM app_users ORDER BY id";
        List<AppUser> users = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        }
        return users;
    }

    public List<AppUser> findEnabledUsersByRole(String roleCode) throws SQLException {
        String sql = """
                SELECT id, username, display_name, role_code, enabled
                FROM app_users
                WHERE role_code = ? AND enabled = 1
                ORDER BY id
                """;
        List<AppUser> users = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roleCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    users.add(mapUser(resultSet));
                }
            }
        }
        return users;
    }

    public List<AppUser> searchUsers(String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) {
            return findAllUsers();
        }
        String sql = """
                SELECT id, username, display_name, role_code, enabled
                FROM app_users
                WHERE username LIKE ? OR display_name LIKE ?
                ORDER BY id
                """;
        List<AppUser> users = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String value = "%" + keyword.trim() + "%";
            statement.setString(1, value);
            statement.setString(2, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    users.add(mapUser(resultSet));
                }
            }
        }
        return users;
    }

    public AppUser saveUser(AppUser user, String password, String changedBy) throws SQLException {
        String sql = """
                INSERT INTO app_users (username, display_name, password_hash, role_code, enabled, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getDisplayName());
            statement.setString(3, hashPassword(password));
            statement.setString(4, user.getRoleCode());
            statement.setInt(5, user.isEnabled() ? 1 : 0);
            statement.setString(6, changedBy);
            statement.setString(7, changedBy);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
        }
        saveUserAudit(user.getId(), "CREATE", changedBy, user.toString());
        return user;
    }

    public void updateUser(AppUser user, String password, String changedBy) throws SQLException {
        String sqlWithPassword = """
                UPDATE app_users
                SET username = ?, display_name = ?, password_hash = ?, role_code = ?, enabled = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        String sqlWithoutPassword = """
                UPDATE app_users
                SET username = ?, display_name = ?, role_code = ?, enabled = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        boolean changePassword = password != null && !password.isBlank();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(changePassword ? sqlWithPassword : sqlWithoutPassword)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getDisplayName());
            if (changePassword) {
                statement.setString(3, hashPassword(password));
                statement.setString(4, user.getRoleCode());
                statement.setInt(5, user.isEnabled() ? 1 : 0);
                statement.setString(6, changedBy);
                statement.setObject(7, user.getId());
            } else {
                statement.setString(3, user.getRoleCode());
                statement.setInt(4, user.isEnabled() ? 1 : 0);
                statement.setString(5, changedBy);
                statement.setObject(6, user.getId());
            }
            statement.executeUpdate();
        }
        saveUserAudit(user.getId(), "UPDATE", changedBy, user.toString());
    }

    public int startLoginRecord(AppUser user) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        String sql = """
                INSERT INTO login_records (user_id, username, display_name, role_code, computer_name, login_date, login_time, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, user.getId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getDisplayName());
            statement.setString(4, user.getRoleCode());
            statement.setString(5, computerName());
            statement.setString(6, rocDate(now));
            statement.setString(7, "%02d:%02d".formatted(now.getHour(), now.getMinute()));
            statement.setString(8, "LOGIN");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void finishLoginRecord(Integer loginRecordId) throws SQLException {
        if (loginRecordId == null || loginRecordId <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String sql = "UPDATE login_records SET logout_date = ?, logout_time = ?, status = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rocDate(now));
            statement.setString(2, "%02d:%02d".formatted(now.getHour(), now.getMinute()));
            statement.setString(3, "LOGOUT");
            statement.setObject(4, loginRecordId);
            statement.executeUpdate();
        }
    }

    public List<LoginRecord> findLoginRecords() throws SQLException {
        String sql = """
                SELECT id, login_date, login_time, computer_name, display_name, logout_date, logout_time
                FROM login_records
                ORDER BY id DESC
                """;
        List<LoginRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                records.add(new LoginRecord(
                        resultSet.getInt("id"),
                        resultSet.getString("login_date"),
                        resultSet.getString("login_time"),
                        resultSet.getString("computer_name"),
                        resultSet.getString("display_name"),
                        resultSet.getString("logout_date"),
                        resultSet.getString("logout_time")
                ));
            }
        }
        return records;
    }

    public List<UserAuditRecord> findUserAudits() throws SQLException {
        String sql = """
                SELECT id, user_id, action, changed_by, changed_at, snapshot
                FROM app_user_audits
                ORDER BY id DESC
                """;
        List<UserAuditRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                records.add(new UserAuditRecord(
                        resultSet.getInt("id"),
                        (Integer) resultSet.getObject("user_id"),
                        resultSet.getString("action"),
                        resultSet.getString("changed_by"),
                        resultSet.getString("changed_at"),
                        resultSet.getString("snapshot")
                ));
            }
        }
        return records;
    }

    private void saveUserAudit(Integer userId, String action, String changedBy, String snapshot) throws SQLException {
        String sql = "INSERT INTO app_user_audits (user_id, action, changed_by, snapshot) VALUES (?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setString(2, action);
            statement.setString(3, changedBy);
            statement.setString(4, snapshot);
            statement.executeUpdate();
        }
    }

    private AppUser mapUser(ResultSet resultSet) throws SQLException {
        return new AppUser(
                resultSet.getInt("id"),
                resultSet.getString("username"),
                resultSet.getString("display_name"),
                resultSet.getString("role_code"),
                resultSet.getInt("enabled") == 1
        );
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("密碼雜湊失敗", e);
        }
    }

    private String computerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getProperty("user.name", "UNKNOWN");
        }
    }

    private String rocDate(LocalDateTime dateTime) {
        return "%03d.%02d.%02d".formatted(dateTime.getYear() - 1911, dateTime.getMonthValue(), dateTime.getDayOfMonth());
    }
}
