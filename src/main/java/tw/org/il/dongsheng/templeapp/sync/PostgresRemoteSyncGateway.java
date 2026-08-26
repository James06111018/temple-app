package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.model.Donation;
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
            String nextToken = latestToken(members, donations, sinceToken);
            return new RemoteSnapshot(nextToken, members, donations);
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

    private String latestToken(List<LightMember> members, List<Donation> donations, String fallback) {
        String latest = fallback;
        for (LightMember member : members) {
            latest = latestToken(latest, member.getUpdatedAt());
        }
        for (Donation donation : donations) {
            latest = latestToken(latest, donation.getUpdatedAt());
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

    private Timestamp toTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Timestamp.valueOf(LocalDateTime.parse(value, LOCAL_TIMESTAMP_FORMATTER));
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
}
