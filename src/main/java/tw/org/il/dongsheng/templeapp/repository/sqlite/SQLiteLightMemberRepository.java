package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.AuthSession;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.MemberBatchUpdateRequest;
import tw.org.il.dongsheng.templeapp.repository.LightMemberRepository;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

public class SQLiteLightMemberRepository implements LightMemberRepository {
    private static final String TABLE_NAME = "light_members";

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteLightMemberRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createTable() throws SQLException {
//        String dropSql = "DROP TABLE IF EXISTS " + TABLE_NAME;
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "phone TEXT," +
                "city TEXT," +
                "dist TEXT," +
                "address TEXT," +
                "zip_code TEXT," +
                "birth_date TEXT," +
                "lunar_birth_date TEXT," +
                "age INTEGER," +
                "zodiac TEXT," +
                "zodiac_year TEXT," +
                "birth_time TEXT," +
                "note TEXT," +
                "contact_person TEXT," +
                "id_number TEXT," +
                "sort_order INTEGER," +
                "ding INTEGER," +
                "kou INTEGER," +
                "is_mail TEXT," +
                "gender TEXT," +
                "is_deleted INTEGER NOT NULL DEFAULT 0" +
                ")";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
//            statement.execute(dropSql);
            statement.execute(sql);
            addColumnIfMissing(connection, "is_deleted", "INTEGER NOT NULL DEFAULT 0");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_light_members_is_deleted ON light_members(is_deleted)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS light_member_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        member_id INTEGER,
                        action TEXT NOT NULL,
                        changed_by TEXT,
                        changed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        snapshot TEXT
                    )
                    """);
        }
    }

    @Override
    public LightMember save(LightMember member) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " (" +
                "name, phone, city, dist, address, zip_code, birth_date, lunar_birth_date, age, zodiac, zodiac_year, " +
                "birth_time, note, contact_person, id_number, sort_order, ding, kou, is_mail, gender" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setCommonFields(statement, member);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    member.setId(generatedKeys.getInt(1));
                }
            }
        }
        saveAudit(member.getId(), "CREATE", AuthSession.getCurrentOperatorName(), member.toString());

        return member;
    }

    @Override
    public boolean update(LightMember member) throws SQLException {
        if (member.getId() == null) {
            throw new IllegalArgumentException("Light_Member id cannot be null when updating.");
        }

        String sql = "UPDATE " + TABLE_NAME + " SET " +
                "name = ?, phone = ?, city = ?, dist = ?, address = ?, zip_code = ?, birth_date = ?, lunar_birth_date = ?, age = ?, " +
                "zodiac = ?, zodiac_year = ?, birth_time = ?, note = ?, contact_person = ?, id_number = ?, sort_order = ?, " +
                "ding = ?, kou = ?, is_mail = ?, gender = ? " +
                "WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        Optional<LightMember> before = findById(member.getId());
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setCommonFields(statement, member);
            statement.setObject(21, member.getId());
            boolean updated = statement.executeUpdate() > 0;
            if (updated) {
                saveAudit(member.getId(), "UPDATE", AuthSession.getCurrentOperatorName(),
                        "before=" + before.map(LightMember::toString).orElse("") + "\nafter=" + member);
            }
            return updated;
        }
    }

    @Override
    public boolean deleteById(int id) throws SQLException {
        Optional<LightMember> before = findById(id);
        String sql = "UPDATE " + TABLE_NAME + " SET is_deleted = 1 WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            boolean deleted = statement.executeUpdate() > 0;
            if (deleted) {
                saveAudit(id, "DELETE", AuthSession.getCurrentOperatorName(),
                        before.map(LightMember::toString).orElse(""));
            }
            return deleted;
        }
    }

    @Override
    public int batchUpdateContact(MemberBatchUpdateRequest request) throws SQLException {
        if (request == null || request.memberIds().isEmpty()
                || (!request.updatePhone() && !request.updateAddress())) {
            return 0;
        }

        List<Integer> memberIds = request.memberIds().stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (memberIds.isEmpty()) {
            return 0;
        }

        String assignments;
        if (request.updatePhone() && request.updateAddress()) {
            assignments = "phone = ?, zip_code = ?, address = ?";
        } else if (request.updatePhone()) {
            assignments = "phone = ?";
        } else {
            assignments = "zip_code = ?, address = ?";
        }
        String updateSql = "UPDATE " + TABLE_NAME + " SET " + assignments
                + " WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Map<Integer, LightMember> beforeMembers = findByIds(connection, memberIds);
                int updatedCount = 0;
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    for (Integer memberId : memberIds) {
                        LightMember before = beforeMembers.get(memberId);
                        if (before == null) {
                            continue;
                        }

                        int parameterIndex = 1;
                        if (request.updatePhone()) {
                            statement.setString(parameterIndex++, request.phone());
                        }
                        if (request.updateAddress()) {
                            statement.setString(parameterIndex++, request.zipCode());
                            statement.setString(parameterIndex++, request.address());
                        }
                        statement.setInt(parameterIndex, memberId);

                        if (statement.executeUpdate() > 0) {
                            LightMember after = copyWithBatchContact(before, request);
                            saveAudit(connection, memberId, "批次修改",
                                    AuthSession.getCurrentOperatorName(),
                                    "before=" + before + "\nafter=" + after);
                            updatedCount++;
                        }
                    }
                }
                connection.commit();
                return updatedCount;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    @Override
    public LightMember reserveBlankMember() throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " DEFAULT VALUES";
        LightMember member = new LightMember();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    member.setId(generatedKeys.getInt(1));
                }
            }
        }
        saveAudit(member.getId(), "RESERVE", AuthSession.getCurrentOperatorName(),
                "reserved blank member id=" + member.getId());
        return member;
    }

    @Override
    public Optional<LightMember> findById(int id) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE id = ? AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<LightMember> findByName(String name) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE name = ? AND COALESCE(is_deleted, 0) = 0";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<LightMember> search(String id, String name, String phone) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM " + TABLE_NAME + " WHERE COALESCE(is_deleted, 0) = 0"
        );
        List<String> params = new ArrayList<>();

        if (!Util.isBlank(id)) {
            sql.append(" AND (CAST(id AS TEXT) LIKE ? OR printf('%07d', id) LIKE ?)");
            String normalizedId = id.trim();
            params.add("%" + Util.trimLeadingZeros(normalizedId) + "%");
            params.add("%" + normalizedId + "%");
        }
        if (!Util.isBlank(name)) {
            sql.append(" AND name LIKE ?");
            params.add("%" + name.trim() + "%");
        }
        if (!Util.isBlank(phone)) {
            sql.append(" AND phone LIKE ?");
            params.add("%" + phone.trim() + "%");
        }

        sql.append(" ORDER BY id DESC");
        List<LightMember> members = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(mapRow(resultSet));
                }
            }
        }
        return members;
    }

    @Override
    public List<LightMember> findByAddress(String keyword, int limit, int offset) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE address = ? AND COALESCE(is_deleted, 0) = 0 ORDER BY id DESC LIMIT ? OFFSET ?";
        List<LightMember> members = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keyword);
            statement.setInt(2, limit);
            statement.setInt(3, offset);

            try(ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(mapRow(resultSet));
                }
            }
        }

        return members;
    }

    @Override
    public int getMemberCount(String keyword) throws SQLException {
        String sql = "SELECT COUNT(1) AS count FROM " + TABLE_NAME +
                " WHERE address = ? AND COALESCE(is_deleted, 0) = 0";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keyword);
            try(ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<LightMember> findAll() throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE COALESCE(is_deleted, 0) = 0 ORDER BY sort_order ASC, id ASC";
        List<LightMember> members = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                members.add(mapRow(resultSet));
            }
        }

        return members;
    }

    @Override
    public List<Integer> findDeletedIds() throws SQLException {
        List<Integer> existingIds = new ArrayList<>();
        Set<Integer> deletedIds = new TreeSet<>();
        String sql = "SELECT id, COALESCE(is_deleted, 0) AS is_deleted FROM " + TABLE_NAME + " ORDER BY id";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                existingIds.add(id);
                if (resultSet.getInt("is_deleted") == 1) {
                    deletedIds.add(id);
                }
            }
        }

        int expected = 1;
        for (Integer id : existingIds) {
            while (expected < id) {
                deletedIds.add(expected++);
            }
            expected = id + 1;
        }
        return new ArrayList<>(deletedIds);
    }

    @Override
    public List<Integer> findBlankNameIds() throws SQLException {
        String sql = "SELECT id FROM " + TABLE_NAME + """
                 WHERE COALESCE(is_deleted, 0) = 0
                   AND COALESCE(TRIM(name), '') = ''
                   AND COALESCE(TRIM(phone), '') = ''
                   AND COALESCE(TRIM(city), '') = ''
                   AND COALESCE(TRIM(dist), '') = ''
                   AND COALESCE(TRIM(address), '') = ''
                   AND COALESCE(TRIM(zip_code), '') = ''
                   AND COALESCE(TRIM(birth_date), '') = ''
                   AND COALESCE(TRIM(lunar_birth_date), '') = ''
                   AND age IS NULL
                   AND COALESCE(TRIM(zodiac), '') = ''
                   AND COALESCE(TRIM(zodiac_year), '') = ''
                   AND COALESCE(TRIM(birth_time), '') = ''
                   AND COALESCE(TRIM(note), '') = ''
                   AND COALESCE(TRIM(contact_person), '') = ''
                   AND COALESCE(TRIM(id_number), '') = ''
                   AND sort_order IS NULL
                   AND ding IS NULL
                   AND kou IS NULL
                   AND COALESCE(TRIM(is_mail), '') = ''
                   AND COALESCE(TRIM(gender), '') = ''
                 ORDER BY id
                """;
        List<Integer> ids = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ids.add(resultSet.getInt("id"));
            }
        }
        return ids;
    }

    @Override
    public int getNextId() throws SQLException{
        String sql = "SELECT MAX(id) FROM " + TABLE_NAME;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1) + 1;
            }
        }
        return 1;
    }

    private void setCommonFields(PreparedStatement statement, LightMember member) throws SQLException {
        statement.setString(1, member.getName());
        statement.setString(2, member.getPhone());
        statement.setString(3, member.getCity());
        statement.setString(4, member.getDist());
        statement.setString(5, member.getAddress());
        statement.setString(6, member.getZipCode());
        statement.setString(7, member.getBirthDate());
        statement.setString(8, member.getLunarBirthDate());
        statement.setObject(9, member.getAge());
        statement.setString(10, member.getZodiac());
        statement.setString(11, member.getZodiacYear());
        statement.setString(12, member.getBirthTime());
        statement.setString(13, member.getNote());
        statement.setString(14, member.getContactPerson());
        statement.setString(15, member.getIdNumber());
        statement.setObject(16, member.getSortOrder());
        statement.setObject(17, member.getDing());
        statement.setObject(18, member.getKou());
        statement.setString(19, member.getIsMail());
        statement.setString(20, member.getGender());
    }

    private void saveAudit(Integer memberId, String action, String changedBy, String snapshot) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            saveAudit(connection, memberId, action, changedBy, snapshot);
        }
    }

    private void saveAudit(Connection connection, Integer memberId, String action,
                           String changedBy, String snapshot) throws SQLException {
        String sql = """
                    INSERT INTO light_member_audits (member_id, action, changed_by, snapshot)
                    VALUES (?, ?, ?, ?)
                    """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, memberId);
            statement.setString(2, action);
            statement.setString(3, changedBy);
            statement.setString(4, snapshot);
            statement.executeUpdate();
        }
    }

    private Map<Integer, LightMember> findByIds(Connection connection, List<Integer> memberIds)
            throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(memberIds.size(), "?"));
        String sql = "SELECT * FROM " + TABLE_NAME
                + " WHERE id IN (" + placeholders + ") AND COALESCE(is_deleted, 0) = 0";
        Map<Integer, LightMember> members = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < memberIds.size(); i++) {
                statement.setInt(i + 1, memberIds.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LightMember member = mapRow(resultSet);
                    members.put(member.getId(), member);
                }
            }
        }
        return members;
    }

    private LightMember copyWithBatchContact(LightMember source, MemberBatchUpdateRequest request) {
        LightMember copy = new LightMember(
                source.getId(), source.getName(), source.getPhone(), source.getCity(), source.getDist(),
                source.getAddress(), source.getZipCode(), source.getBirthDate(), source.getLunarBirthDate(),
                source.getAge(), source.getZodiac(), source.getZodiacYear(), source.getBirthTime(),
                source.getNote(), source.getContactPerson(), source.getIdNumber(), source.getSortOrder(),
                source.getDing(), source.getKou(), source.getIsMail(), source.getGender()
        );
        if (request.updatePhone()) {
            copy.setPhone(request.phone());
        }
        if (request.updateAddress()) {
            copy.setZipCode(request.zipCode());
            copy.setAddress(request.address());
        }
        return copy;
    }

    private void addColumnIfMissing(Connection connection, String columnName, String definition) throws SQLException {
        boolean exists = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + TABLE_NAME + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + definition);
            }
        }
    }

    private LightMember mapRow(ResultSet resultSet) throws SQLException {
        LightMember member = new LightMember();
        member.setId(resultSet.getInt("id"));
        member.setName(resultSet.getString("name"));
        member.setPhone(resultSet.getString("phone"));
        member.setCity(resultSet.getString("city"));
        member.setDist(resultSet.getString("dist"));
        member.setAddress(resultSet.getString("address"));
        member.setZipCode(resultSet.getString("zip_code"));
        member.setBirthDate(resultSet.getString("birth_date"));
        member.setLunarBirthDate(resultSet.getString("lunar_birth_date"));
        member.setAge((Integer) resultSet.getObject("age"));
        member.setZodiac(resultSet.getString("zodiac"));
        member.setZodiacYear(resultSet.getString("zodiac_year"));
        member.setBirthTime(resultSet.getString("birth_time"));
        member.setNote(resultSet.getString("note"));
        member.setContactPerson(resultSet.getString("contact_person"));
        member.setIdNumber(resultSet.getString("id_number"));
        member.setSortOrder((Integer) resultSet.getObject("sort_order"));
        member.setDing((Integer) resultSet.getObject("ding"));
        member.setKou((Integer) resultSet.getObject("kou"));
        member.setIsMail(resultSet.getString("is_mail"));
        member.setGender(resultSet.getString("gender"));
        return member;
    }
}
