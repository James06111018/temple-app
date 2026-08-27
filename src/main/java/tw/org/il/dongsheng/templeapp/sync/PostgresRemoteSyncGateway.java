package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.AppFunction;
import tw.org.il.dongsheng.templeapp.model.AppRole;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PostgresRemoteSyncGateway implements RemoteSyncGateway {
    private static final DateTimeFormatter LOCAL_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresRemoteSyncGateway(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public RemoteSnapshot fetchChanges(String sinceToken) {
        try (Connection connection = openConnection()) {
            List<LightMember> members = fetchMembers(connection, sinceToken);
            List<Donation> donations = fetchDonations(connection, sinceToken);
            List<LightNumberSyncRow> lightNumbers = fetchLightNumbers(connection, sinceToken);
            List<HouseholdLightSyncRow> householdLightRecords = fetchHouseholdLightRecords(connection, sinceToken);
            List<DonationSupplementSyncRow> donationSupplements = fetchDonationSupplements(connection, sinceToken);
            String nextToken = latestToken(members, donations, lightNumbers, householdLightRecords, donationSupplements, sinceToken);
            return new RemoteSnapshot(nextToken, members, donations, lightNumbers, householdLightRecords, donationSupplements);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch remote changes.", ex);
        }
    }

    @Override
    public void pushMembers(List<LightMember> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO light_members (
                    uuid, name, phone, city, dist, address, zip_code, birth_date, lunar_birth_date, age, zodiac, zodiac_year,
                    birth_time, note, contact_person, id_number, sort_order, ding, kou, is_mail, gender, is_deleted,
                    updated_at, deleted_at, version, device_id, sync_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (uuid) DO UPDATE SET
                    name = EXCLUDED.name,
                    phone = EXCLUDED.phone,
                    city = EXCLUDED.city,
                    dist = EXCLUDED.dist,
                    address = EXCLUDED.address,
                    zip_code = EXCLUDED.zip_code,
                    birth_date = EXCLUDED.birth_date,
                    lunar_birth_date = EXCLUDED.lunar_birth_date,
                    age = EXCLUDED.age,
                    zodiac = EXCLUDED.zodiac,
                    zodiac_year = EXCLUDED.zodiac_year,
                    birth_time = EXCLUDED.birth_time,
                    note = EXCLUDED.note,
                    contact_person = EXCLUDED.contact_person,
                    id_number = EXCLUDED.id_number,
                    sort_order = EXCLUDED.sort_order,
                    ding = EXCLUDED.ding,
                    kou = EXCLUDED.kou,
                    is_mail = EXCLUDED.is_mail,
                    gender = EXCLUDED.gender,
                    is_deleted = EXCLUDED.is_deleted,
                    updated_at = EXCLUDED.updated_at,
                    deleted_at = EXCLUDED.deleted_at,
                    version = EXCLUDED.version,
                    device_id = EXCLUDED.device_id,
                    sync_status = EXCLUDED.sync_status
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LightMember member : members) {
                try {
                    bindMember(statement, member);
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    throw new IllegalStateException(
                            "Failed to push member id=" + member.getId()
                                    + ", uuid=" + member.getUuid()
                                    + ", name=" + member.getName()
                                    + ": " + ex.getMessage(),
                            ex
                    );
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to push members.", ex);
        }
    }

    @Override
    public void pushDonations(List<Donation> donations) {
        if (donations == null || donations.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO donations (
                    uuid, member_id, receipt_no, donate_date, extra_no, amount, summary, donate_note, other_note,
                    donor_no, light_no, should_pay, donate_type, creator, is_deleted, updated_at, deleted_at, version,
                    device_id, sync_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (uuid) DO UPDATE SET
                    member_id = EXCLUDED.member_id,
                    receipt_no = EXCLUDED.receipt_no,
                    donate_date = EXCLUDED.donate_date,
                    extra_no = EXCLUDED.extra_no,
                    amount = EXCLUDED.amount,
                    summary = EXCLUDED.summary,
                    donate_note = EXCLUDED.donate_note,
                    other_note = EXCLUDED.other_note,
                    donor_no = EXCLUDED.donor_no,
                    light_no = EXCLUDED.light_no,
                    should_pay = EXCLUDED.should_pay,
                    donate_type = EXCLUDED.donate_type,
                    creator = EXCLUDED.creator,
                    is_deleted = EXCLUDED.is_deleted,
                    updated_at = EXCLUDED.updated_at,
                    deleted_at = EXCLUDED.deleted_at,
                    version = EXCLUDED.version,
                    device_id = EXCLUDED.device_id,
                    sync_status = EXCLUDED.sync_status
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Donation donation : donations) {
                try {
                    bindDonation(statement, donation);
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    throw new IllegalStateException(
                            "Failed to push donation id=" + donation.getId()
                                    + ", uuid=" + donation.getUuid()
                                    + ", memberId=" + donation.getMemberId()
                                    + ": " + ex.getMessage(),
                            ex
                    );
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to push donations.", ex);
        }
    }

    @Override
    public void pushLightNumbers(List<LightNumberSyncRow> lightNumbers) {
        if (lightNumbers == null || lightNumbers.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO light_numbers (
                    id, management_type, light_type, serial_number, member_id, principal_name, status,
                    created_by, created_at, updated_by, updated_at, registered_at, deleted_by, deleted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (management_type, light_type, serial_number) DO UPDATE SET
                    member_id = EXCLUDED.member_id,
                    principal_name = EXCLUDED.principal_name,
                    status = EXCLUDED.status,
                    created_by = EXCLUDED.created_by,
                    created_at = EXCLUDED.created_at,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = EXCLUDED.updated_at,
                    registered_at = EXCLUDED.registered_at,
                    deleted_by = EXCLUDED.deleted_by,
                    deleted_at = EXCLUDED.deleted_at
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LightNumberSyncRow row : lightNumbers) {
                try {
                    bindLightNumber(statement, row);
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    throw new IllegalStateException("Failed to push light number id=" + row.getId()
                            + ", managementType=" + row.getManagementType()
                            + ", lightType=" + row.getLightType()
                            + ", serialNumber=" + row.getSerialNumber()
                            + ": " + ex.getMessage(), ex);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to push light numbers.", ex);
        }
    }

    @Override
    public void pushHouseholdLightRecords(List<HouseholdLightSyncRow> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO household_light_records (
                    id, member_id, light_type_id, roc_year, light_no, note,
                    created_by, created_at, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (member_id, light_type_id, roc_year) DO UPDATE SET
                    light_no = EXCLUDED.light_no,
                    note = EXCLUDED.note,
                    created_by = EXCLUDED.created_by,
                    created_at = EXCLUDED.created_at,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = EXCLUDED.updated_at
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (HouseholdLightSyncRow row : records) {
                try {
                    bindHouseholdLight(statement, row);
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    throw new IllegalStateException("Failed to push household light record id=" + row.getId()
                            + ", memberId=" + row.getMemberId()
                            + ", lightTypeId=" + row.getLightTypeId()
                            + ", rocYear=" + row.getRocYear()
                            + ": " + ex.getMessage(), ex);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to push household light records.", ex);
        }
    }

    @Override
    public void pushDonationSupplements(List<DonationSupplementSyncRow> supplements) {
        if (supplements == null || supplements.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO donation_supplements (
                    id, donation_id, supplement_date, supplement_no, source_type,
                    created_by, created_at, updated_by, updated_at, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (donation_id) DO UPDATE SET
                    supplement_date = EXCLUDED.supplement_date,
                    supplement_no = EXCLUDED.supplement_no,
                    source_type = EXCLUDED.source_type,
                    created_by = EXCLUDED.created_by,
                    created_at = EXCLUDED.created_at,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = EXCLUDED.updated_at,
                    is_deleted = EXCLUDED.is_deleted
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (DonationSupplementSyncRow row : supplements) {
                try {
                    bindDonationSupplement(statement, row);
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    throw new IllegalStateException("Failed to push donation supplement id=" + row.getId()
                            + ", donationId=" + row.getDonationId()
                            + ": " + ex.getMessage(), ex);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to push donation supplements.", ex);
        }
    }

    @Override
    public void replaceAppUsers(List<AuthUserSyncRow> users) {
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM app_users");
            }
            if (users == null || users.isEmpty()) {
                return;
            }
            String sql = """
                    INSERT INTO app_users (
                        id, username, display_name, password_hash, role_code, enabled,
                        created_by, created_at, updated_by, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (username) DO UPDATE SET
                        display_name = EXCLUDED.display_name,
                        password_hash = EXCLUDED.password_hash,
                        role_code = EXCLUDED.role_code,
                        enabled = EXCLUDED.enabled,
                        created_by = EXCLUDED.created_by,
                        created_at = EXCLUDED.created_at,
                        updated_by = EXCLUDED.updated_by,
                        updated_at = EXCLUDED.updated_at
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (AuthUserSyncRow row : users) {
                    bindAuthUser(statement, row);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to replace app users.", ex);
        }
    }

    @Override
    public void replaceAppRoles(List<AppRole> roles) {
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM app_users");
                statement.executeUpdate("DELETE FROM role_functions");
                statement.executeUpdate("DELETE FROM app_roles");
            }
            if (roles == null || roles.isEmpty()) {
                return;
            }
            String sql = """
                    INSERT INTO app_roles (role_code, role_name)
                    VALUES (?, ?)
                    ON CONFLICT (role_code) DO UPDATE SET
                        role_name = EXCLUDED.role_name
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (AppRole role : roles) {
                    statement.setString(1, role.getRoleCode());
                    statement.setString(2, role.getRoleName());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to replace app roles.", ex);
        }
    }

    @Override
    public void replaceAppFunctions(List<AppFunction> functions) {
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM app_functions");
            }
            if (functions == null || functions.isEmpty()) {
                return;
            }
            String sql = """
                    INSERT INTO app_functions (function_code, function_name, enabled)
                    VALUES (?, ?, ?)
                    ON CONFLICT (function_code) DO UPDATE SET
                        function_name = EXCLUDED.function_name,
                        enabled = EXCLUDED.enabled
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (AppFunction function : functions) {
                    statement.setString(1, function.getFunctionCode());
                    statement.setString(2, function.getFunctionName());
                    statement.setInt(3, function.isEnabled() ? 1 : 0);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to replace app functions.", ex);
        }
    }

    @Override
    public void replaceRoleFunctions(List<RoleFunctionSyncRow> roleFunctions) {
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM role_functions");
            }
            if (roleFunctions == null || roleFunctions.isEmpty()) {
                return;
            }
            String sql = """
                    INSERT INTO role_functions (role_code, function_code)
                    VALUES (?, ?)
                    ON CONFLICT (role_code, function_code) DO NOTHING
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (RoleFunctionSyncRow row : roleFunctions) {
                    statement.setString(1, row.getRoleCode());
                    statement.setString(2, row.getFunctionCode());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to replace role functions.", ex);
        }
    }

    @Override
    public List<AuthUserSyncRow> fetchAppUsers() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT id, username, display_name, password_hash, role_code, enabled,
                            created_by, created_at, updated_by, updated_at
                     FROM app_users
                     ORDER BY id
                     """)) {
            List<AuthUserSyncRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new AuthUserSyncRow(
                        resultSet.getInt("id"),
                        resultSet.getString("username"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("role_code"),
                        resultSet.getInt("enabled") == 1,
                        resultSet.getString("created_by"),
                        timestampToString(resultSet.getTimestamp("created_at")),
                        resultSet.getString("updated_by"),
                        timestampToString(resultSet.getTimestamp("updated_at"))
                ));
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch app users.", ex);
        }
    }

    @Override
    public List<AppRole> fetchAppRoles() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT role_code, role_name FROM app_roles ORDER BY role_code")) {
            List<AppRole> roles = new ArrayList<>();
            while (resultSet.next()) {
                roles.add(new AppRole(resultSet.getString("role_code"), resultSet.getString("role_name")));
            }
            return roles;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch app roles.", ex);
        }
    }

    @Override
    public List<AppFunction> fetchAppFunctions() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT function_code, function_name, enabled FROM app_functions ORDER BY function_code")) {
            List<AppFunction> functions = new ArrayList<>();
            while (resultSet.next()) {
                functions.add(new AppFunction(
                        resultSet.getString("function_code"),
                        resultSet.getString("function_name"),
                        resultSet.getInt("enabled") == 1
                ));
            }
            return functions;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch app functions.", ex);
        }
    }

    @Override
    public List<RoleFunctionSyncRow> fetchRoleFunctions() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT role_code, function_code FROM role_functions ORDER BY role_code, function_code")) {
            List<RoleFunctionSyncRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new RoleFunctionSyncRow(resultSet.getString("role_code"), resultSet.getString("function_code")));
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch role functions.", ex);
        }
    }

    private Connection openConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("PostgreSQL driver not found.", ex);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private List<LightMember> fetchMembers(Connection connection, String sinceToken) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM light_members");
        List<Object> params = new ArrayList<>();
        if (sinceToken != null && !sinceToken.isBlank()) {
            sql.append(" WHERE updated_at > ?::timestamptz");
            params.add(sinceToken);
        }
        sql.append(" ORDER BY updated_at ASC NULLS FIRST, id ASC");
        List<LightMember> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(mapMember(resultSet));
                }
            }
        }
        return members;
    }

    private List<Donation> fetchDonations(Connection connection, String sinceToken) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM donations");
        List<Object> params = new ArrayList<>();
        if (sinceToken != null && !sinceToken.isBlank()) {
            sql.append(" WHERE updated_at > ?::timestamptz");
            params.add(sinceToken);
        }
        sql.append(" ORDER BY updated_at ASC NULLS FIRST, id ASC");
        List<Donation> donations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    donations.add(mapDonation(resultSet));
                }
            }
        }
        return donations;
    }

    private List<LightNumberSyncRow> fetchLightNumbers(Connection connection, String sinceToken) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM light_numbers");
        List<Object> params = new ArrayList<>();
        if (sinceToken != null && !sinceToken.isBlank()) {
            sql.append(" WHERE updated_at > ?::timestamptz");
            params.add(sinceToken);
        }
        sql.append(" ORDER BY updated_at ASC NULLS FIRST, id ASC");
        List<LightNumberSyncRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapLightNumber(resultSet));
                }
            }
        }
        return rows;
    }

    private List<HouseholdLightSyncRow> fetchHouseholdLightRecords(Connection connection, String sinceToken) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM household_light_records");
        List<Object> params = new ArrayList<>();
        if (sinceToken != null && !sinceToken.isBlank()) {
            sql.append(" WHERE updated_at > ?::timestamptz");
            params.add(sinceToken);
        }
        sql.append(" ORDER BY updated_at ASC NULLS FIRST, id ASC");
        List<HouseholdLightSyncRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapHouseholdLight(resultSet));
                }
            }
        }
        return rows;
    }

    private List<DonationSupplementSyncRow> fetchDonationSupplements(Connection connection, String sinceToken) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM donation_supplements");
        List<Object> params = new ArrayList<>();
        if (sinceToken != null && !sinceToken.isBlank()) {
            sql.append(" WHERE updated_at > ?::timestamptz");
            params.add(sinceToken);
        }
        sql.append(" ORDER BY updated_at ASC NULLS FIRST, id ASC");
        List<DonationSupplementSyncRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapDonationSupplement(resultSet));
                }
            }
        }
        return rows;
    }

    private String latestToken(
            List<LightMember> members,
            List<Donation> donations,
            List<LightNumberSyncRow> lightNumbers,
            List<HouseholdLightSyncRow> householdLightRecords,
            List<DonationSupplementSyncRow> donationSupplements,
            String fallback
    ) {
        String latest = fallback;
        for (LightMember member : members) {
            latest = latestToken(latest, member.getUpdatedAt());
        }
        for (Donation donation : donations) {
            latest = latestToken(latest, donation.getUpdatedAt());
        }
        for (LightNumberSyncRow row : lightNumbers) {
            latest = latestToken(latest, row.getUpdatedAt());
        }
        for (HouseholdLightSyncRow row : householdLightRecords) {
            latest = latestToken(latest, row.getUpdatedAt());
        }
        for (DonationSupplementSyncRow row : donationSupplements) {
            latest = latestToken(latest, row.getUpdatedAt());
        }
        return latest;
    }

    private String latestToken(String current, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return candidate;
        }
        return candidate.compareTo(current) > 0 ? candidate : current;
    }

    private void bindParams(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }

    private void bindMember(PreparedStatement statement, LightMember member) throws SQLException {
        statement.setString(1, member.getUuid());
        statement.setString(2, member.getName());
        statement.setString(3, member.getPhone());
        statement.setString(4, member.getCity());
        statement.setString(5, member.getDist());
        statement.setString(6, member.getAddress());
        statement.setString(7, member.getZipCode());
        statement.setString(8, member.getBirthDate());
        statement.setString(9, member.getLunarBirthDate());
        statement.setObject(10, member.getAge());
        statement.setString(11, member.getZodiac());
        statement.setString(12, member.getZodiacYear());
        statement.setString(13, member.getBirthTime());
        statement.setString(14, member.getNote());
        statement.setString(15, member.getContactPerson());
        statement.setString(16, member.getIdNumber());
        statement.setObject(17, member.getSortOrder());
        statement.setObject(18, member.getDing());
        statement.setObject(19, member.getKou());
        statement.setString(20, member.getIsMail());
        statement.setString(21, member.getGender());
        statement.setObject(22, member.getDeletedAt() != null ? 1 : 0);
        statement.setTimestamp(23, toTimestamp(member.getUpdatedAt()));
        statement.setTimestamp(24, toTimestamp(member.getDeletedAt()));
        statement.setObject(25, member.getVersion());
        statement.setString(26, member.getDeviceId());
        statement.setString(27, member.getSyncStatus());
    }

    private void bindDonation(PreparedStatement statement, Donation donation) throws SQLException {
        statement.setString(1, donation.getUuid());
        statement.setObject(2, donation.getMemberId());
        statement.setString(3, donation.getReceiptNo());
        statement.setString(4, donation.getDonateDate());
        statement.setString(5, donation.getExtraNo());
        statement.setObject(6, donation.getAmount());
        statement.setString(7, donation.getSummary());
        statement.setString(8, donation.getDonateNote());
        statement.setString(9, donation.getOtherNote());
        statement.setString(10, donation.getDonorNo());
        statement.setString(11, donation.getLightNo());
        statement.setObject(12, donation.getShouldPay());
        statement.setString(13, donation.getDonateType());
        statement.setString(14, donation.getCreator());
        statement.setObject(15, donation.getDeletedAt() != null ? 1 : 0);
        statement.setTimestamp(16, toTimestamp(donation.getUpdatedAt()));
        statement.setTimestamp(17, toTimestamp(donation.getDeletedAt()));
        statement.setObject(18, donation.getVersion());
        statement.setString(19, donation.getDeviceId());
        statement.setString(20, donation.getSyncStatus());
    }

    private void bindLightNumber(PreparedStatement statement, LightNumberSyncRow row) throws SQLException {
        statement.setObject(1, row.getId());
        statement.setString(2, row.getManagementType());
        statement.setString(3, row.getLightType());
        statement.setObject(4, row.getSerialNumber());
        statement.setObject(5, row.getMemberId());
        statement.setString(6, row.getPrincipalName());
        statement.setString(7, row.getStatus());
        statement.setString(8, row.getCreatedBy());
        statement.setTimestamp(9, toTimestamp(row.getCreatedAt()));
        statement.setString(10, row.getUpdatedBy());
        statement.setTimestamp(11, toTimestamp(row.getUpdatedAt()));
        statement.setTimestamp(12, toTimestamp(row.getRegisteredAt()));
        statement.setString(13, row.getDeletedBy());
        statement.setTimestamp(14, toTimestamp(row.getDeletedAt()));
    }

    private void bindHouseholdLight(PreparedStatement statement, HouseholdLightSyncRow row) throws SQLException {
        statement.setObject(1, row.getId());
        statement.setObject(2, row.getMemberId());
        statement.setObject(3, row.getLightTypeId());
        statement.setObject(4, row.getRocYear());
        statement.setString(5, row.getLightNo());
        statement.setString(6, row.getNote());
        statement.setString(7, row.getCreatedBy());
        statement.setTimestamp(8, toTimestamp(row.getCreatedAt()));
        statement.setString(9, row.getUpdatedBy());
        statement.setTimestamp(10, toTimestamp(row.getUpdatedAt()));
    }

    private void bindDonationSupplement(PreparedStatement statement, DonationSupplementSyncRow row) throws SQLException {
        statement.setObject(1, row.getId());
        statement.setObject(2, row.getDonationId());
        statement.setString(3, row.getSupplementDate());
        statement.setString(4, row.getSupplementNo());
        statement.setString(5, row.getSourceType());
        statement.setString(6, row.getCreatedBy());
        statement.setTimestamp(7, toTimestamp(row.getCreatedAt()));
        statement.setString(8, row.getUpdatedBy());
        statement.setTimestamp(9, toTimestamp(row.getUpdatedAt()));
        statement.setObject(10, row.getDeleted() == null ? 0 : row.getDeleted());
    }

    private void bindAuthUser(PreparedStatement statement, AuthUserSyncRow row) throws SQLException {
        statement.setObject(1, row.getId());
        statement.setString(2, row.getUsername());
        statement.setString(3, row.getDisplayName());
        statement.setString(4, row.getPasswordHash());
        statement.setString(5, row.getRoleCode());
        statement.setInt(6, row.isEnabled() ? 1 : 0);
        statement.setString(7, row.getCreatedBy());
        statement.setTimestamp(8, toTimestamp(row.getCreatedAt()));
        statement.setString(9, row.getUpdatedBy());
        statement.setTimestamp(10, toTimestamp(row.getUpdatedAt()));
    }

    private Timestamp toTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Timestamp.valueOf(LocalDateTime.parse(value, LOCAL_TIMESTAMP_FORMATTER));
    }

    private String timestampToString(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().format(LOCAL_TIMESTAMP_FORMATTER);
    }

    private LightMember mapMember(ResultSet resultSet) throws SQLException {
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
        member.setUuid(resultSet.getString("uuid"));
        member.setUpdatedAt(resultSet.getString("updated_at"));
        member.setDeletedAt(resultSet.getString("deleted_at"));
        member.setVersion((Integer) resultSet.getObject("version"));
        member.setDeviceId(resultSet.getString("device_id"));
        member.setSyncStatus(resultSet.getString("sync_status"));
        return member;
    }

    private Donation mapDonation(ResultSet resultSet) throws SQLException {
        Donation donation = new Donation();
        donation.setId(resultSet.getInt("id"));
        donation.setMemberId(resultSet.getInt("member_id"));
        donation.setReceiptNo(resultSet.getString("receipt_no"));
        donation.setDonateDate(resultSet.getString("donate_date"));
        donation.setExtraNo(resultSet.getString("extra_no"));
        donation.setAmount((Integer) resultSet.getObject("amount"));
        donation.setSummary(resultSet.getString("summary"));
        donation.setDonateNote(resultSet.getString("donate_note"));
        donation.setOtherNote(resultSet.getString("other_note"));
        donation.setDonorNo(resultSet.getString("donor_no"));
        donation.setLightNo(resultSet.getString("light_no"));
        donation.setShouldPay((Integer) resultSet.getObject("should_pay"));
        donation.setDonateType(resultSet.getString("donate_type"));
        donation.setCreator(resultSet.getString("creator"));
        donation.setUuid(resultSet.getString("uuid"));
        donation.setUpdatedAt(resultSet.getString("updated_at"));
        donation.setDeletedAt(resultSet.getString("deleted_at"));
        donation.setVersion((Integer) resultSet.getObject("version"));
        donation.setDeviceId(resultSet.getString("device_id"));
        donation.setSyncStatus(resultSet.getString("sync_status"));
        return donation;
    }

    private LightNumberSyncRow mapLightNumber(ResultSet resultSet) throws SQLException {
        LightNumberSyncRow row = new LightNumberSyncRow();
        row.setId(resultSet.getInt("id"));
        row.setManagementType(resultSet.getString("management_type"));
        row.setLightType(resultSet.getString("light_type"));
        row.setSerialNumber(resultSet.getInt("serial_number"));
        int memberId = resultSet.getInt("member_id");
        row.setMemberId(resultSet.wasNull() ? null : memberId);
        row.setPrincipalName(resultSet.getString("principal_name"));
        row.setStatus(resultSet.getString("status"));
        row.setCreatedBy(resultSet.getString("created_by"));
        row.setCreatedAt(resultSet.getString("created_at"));
        row.setUpdatedBy(resultSet.getString("updated_by"));
        row.setUpdatedAt(resultSet.getString("updated_at"));
        row.setRegisteredAt(resultSet.getString("registered_at"));
        row.setDeletedBy(resultSet.getString("deleted_by"));
        row.setDeletedAt(resultSet.getString("deleted_at"));
        return row;
    }

    private HouseholdLightSyncRow mapHouseholdLight(ResultSet resultSet) throws SQLException {
        HouseholdLightSyncRow row = new HouseholdLightSyncRow();
        row.setId(resultSet.getInt("id"));
        row.setMemberId(resultSet.getInt("member_id"));
        row.setLightTypeId(resultSet.getInt("light_type_id"));
        row.setRocYear(resultSet.getInt("roc_year"));
        row.setLightNo(resultSet.getString("light_no"));
        row.setNote(resultSet.getString("note"));
        row.setCreatedBy(resultSet.getString("created_by"));
        row.setCreatedAt(resultSet.getString("created_at"));
        row.setUpdatedBy(resultSet.getString("updated_by"));
        row.setUpdatedAt(resultSet.getString("updated_at"));
        return row;
    }

    private DonationSupplementSyncRow mapDonationSupplement(ResultSet resultSet) throws SQLException {
        DonationSupplementSyncRow row = new DonationSupplementSyncRow();
        row.setId(resultSet.getInt("id"));
        row.setDonationId(resultSet.getInt("donation_id"));
        row.setSupplementDate(resultSet.getString("supplement_date"));
        row.setSupplementNo(resultSet.getString("supplement_no"));
        row.setSourceType(resultSet.getString("source_type"));
        row.setCreatedBy(resultSet.getString("created_by"));
        row.setCreatedAt(resultSet.getString("created_at"));
        row.setUpdatedBy(resultSet.getString("updated_by"));
        row.setUpdatedAt(resultSet.getString("updated_at"));
        row.setDeleted(resultSet.getInt("is_deleted"));
        return row;
    }
}
