package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteSyncStateRepository;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SyncService {
    private final SQLiteLightMemberRepository lightMemberRepository;
    private final SQLiteDonationRepository donationRepository;
    private final SQLiteSyncStateRepository syncStateRepository;
    private final RemoteSyncGateway remoteSyncGateway;
    private final SyncState syncState;
    private final Properties syncProperties;

    public SyncService(
            SQLiteLightMemberRepository lightMemberRepository,
            SQLiteDonationRepository donationRepository,
            SQLiteSyncStateRepository syncStateRepository,
            RemoteSyncGateway remoteSyncGateway
    ) throws SQLException {
        this(lightMemberRepository, donationRepository, syncStateRepository, remoteSyncGateway, SyncConfig.load());
    }

    public SyncService(
            SQLiteLightMemberRepository lightMemberRepository,
            SQLiteDonationRepository donationRepository,
            SQLiteSyncStateRepository syncStateRepository,
            RemoteSyncGateway remoteSyncGateway,
            Properties syncProperties
    ) throws SQLException {
        this.lightMemberRepository = lightMemberRepository;
        this.donationRepository = donationRepository;
        this.syncStateRepository = syncStateRepository;
        this.remoteSyncGateway = remoteSyncGateway;
        this.syncProperties = syncProperties == null ? new Properties() : syncProperties;
        this.syncState = syncStateRepository.load();
        if (this.syncState.getDeviceId() == null || this.syncState.getDeviceId().isBlank()) {
            this.syncState.setDeviceId("local");
        }
        String remoteBaseUrl = firstNonBlank(
                this.syncProperties.getProperty("sync.remote.baseUrl"),
                this.syncState.getRemoteBaseUrl()
        );
        if (remoteBaseUrl != null && !remoteBaseUrl.isBlank()) {
            this.syncState.setRemoteBaseUrl(remoteBaseUrl);
        }
    }

    public SyncResult syncNow() {
        SyncResult pushResult = push();
        if (!pushResult.isSuccess()) {
            return pushResult;
        }
        return pull();
    }

    public SyncResult pull() {
        try {
            if (remoteSyncGateway == null) {
                syncState.setLastPullAt(Util.nowUtc());
                syncState.setLastSyncAt(Util.nowUtc());
                syncStateRepository.save(syncState);
                return new SyncResult(true, "No remote gateway configured; local sync state updated.");
            }

            RemoteSnapshot snapshot = remoteSyncGateway.fetchChanges(syncState.getLastSyncToken());
            if (snapshot == null) {
                syncState.setLastPullAt(Util.nowUtc());
                syncState.setLastSyncAt(Util.nowUtc());
                syncStateRepository.save(syncState);
                return new SyncResult(true, "Remote snapshot is empty.");
            }

            applyRemoteMembers(snapshot.getMembers());
            applyRemoteDonations(snapshot.getDonations());

            syncState.setLastPullAt(Util.nowUtc());
            syncState.setLastSyncAt(Util.nowUtc());
            syncState.setLastSyncToken(snapshot.getNextToken());
            syncStateRepository.save(syncState);
            return new SyncResult(true, "Pulled remote changes.");
        } catch (SQLException ex) {
            return new SyncResult(false, "Pull failed: " + ex.getMessage());
        }
    }

    public SyncResult push() {
        try {
            List<LightMember> dirtyMembers = loadDirtyMembers();
            List<Donation> dirtyDonations = loadDirtyDonations();

            if (remoteSyncGateway != null) {
                if (!dirtyMembers.isEmpty()) {
                    remoteSyncGateway.pushMembers(dirtyMembers);
                }
                if (!dirtyDonations.isEmpty()) {
                    remoteSyncGateway.pushDonations(dirtyDonations);
                }
            }

            markMembersSynced(dirtyMembers);
            markDonationsSynced(dirtyDonations);

            syncState.setLastPushAt(Util.nowUtc());
            syncState.setLastSyncAt(Util.nowUtc());
            syncStateRepository.save(syncState);
            return new SyncResult(true, "Pushed local changes.");
        } catch (SQLException ex) {
            return new SyncResult(false, "Push failed: " + ex.getMessage());
        }
    }

    public SyncState getSyncState() {
        return syncState;
    }

    private void applyRemoteMembers(List<LightMember> remoteMembers) throws SQLException {
        if (remoteMembers == null || remoteMembers.isEmpty()) {
            return;
        }
        try (Connection connection = lightMemberRepository.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (LightMember remote : remoteMembers) {
                    mergeMember(connection, remote);
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void applyRemoteDonations(List<Donation> remoteDonations) throws SQLException {
        if (remoteDonations == null || remoteDonations.isEmpty()) {
            return;
        }
        try (Connection connection = donationRepository.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Donation remote : remoteDonations) {
                    mergeDonation(connection, remote);
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void mergeMember(Connection connection, LightMember remote) throws SQLException {
        LightMember local = findMemberByUuid(connection, remote.getUuid());
        if (local == null) {
            insertMember(connection, remote, "clean");
            return;
        }
        if (shouldReplace(local.getVersion(), remote.getVersion(), local.getUpdatedAt(), remote.getUpdatedAt())) {
            updateMemberByUuid(connection, remote, "clean");
        } else if (!safeEquals(local.getUpdatedAt(), remote.getUpdatedAt())) {
            setMemberConflict(connection, local.getUuid());
        }
    }

    private void mergeDonation(Connection connection, Donation remote) throws SQLException {
        Donation local = findDonationByUuid(connection, remote.getUuid());
        if (local == null) {
            insertDonation(connection, remote, "clean");
            return;
        }
        if (shouldReplace(local.getVersion(), remote.getVersion(), local.getUpdatedAt(), remote.getUpdatedAt())) {
            updateDonationByUuid(connection, remote, "clean");
        } else if (!safeEquals(local.getUpdatedAt(), remote.getUpdatedAt())) {
            setDonationConflict(connection, local.getUuid());
        }
    }

    private boolean shouldReplace(Integer localVersion, Integer remoteVersion, String localUpdatedAt, String remoteUpdatedAt) {
        int lv = localVersion == null ? 0 : localVersion;
        int rv = remoteVersion == null ? 0 : remoteVersion;
        if (rv != lv) {
            return rv > lv;
        }
        if (localUpdatedAt == null) {
            return true;
        }
        if (remoteUpdatedAt == null) {
            return false;
        }
        return remoteUpdatedAt.compareTo(localUpdatedAt) >= 0;
    }

    private List<LightMember> loadDirtyMembers() throws SQLException {
        List<LightMember> members = new ArrayList<>();
        String sql = "SELECT * FROM light_members WHERE COALESCE(sync_status, 'clean') IN ('dirty', 'deleted', 'conflict')";
        try (Connection connection = lightMemberRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                members.add(mapMember(resultSet));
            }
        }
        return members;
    }

    private List<Donation> loadDirtyDonations() throws SQLException {
        List<Donation> donations = new ArrayList<>();
        String sql = "SELECT * FROM donations WHERE COALESCE(sync_status, 'clean') IN ('dirty', 'deleted', 'conflict')";
        try (Connection connection = donationRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                donations.add(mapDonation(resultSet));
            }
        }
        return donations;
    }

    private void markMembersSynced(List<LightMember> members) throws SQLException {
        if (members.isEmpty()) {
            return;
        }
        String sql = "UPDATE light_members SET sync_status = 'clean' WHERE uuid = ?";
        try (Connection connection = lightMemberRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LightMember member : members) {
                if (member.getUuid() == null) {
                    continue;
                }
                statement.setString(1, member.getUuid());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void markDonationsSynced(List<Donation> donations) throws SQLException {
        if (donations.isEmpty()) {
            return;
        }
        String sql = "UPDATE donations SET sync_status = 'clean' WHERE uuid = ?";
        try (Connection connection = donationRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Donation donation : donations) {
                if (donation.getUuid() == null) {
                    continue;
                }
                statement.setString(1, donation.getUuid());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private LightMember findMemberByUuid(Connection connection, String uuid) throws SQLException {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM light_members WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapMember(resultSet) : null;
            }
        }
    }

    private Donation findDonationByUuid(Connection connection, String uuid) throws SQLException {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM donations WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapDonation(resultSet) : null;
            }
        }
    }

    private void insertMember(Connection connection, LightMember member, String syncStatus) throws SQLException {
        String sql = """
                INSERT INTO light_members (
                    name, phone, city, dist, address, zip_code, birth_date, lunar_birth_date, age, zodiac, zodiac_year,
                    birth_time, note, contact_person, id_number, sort_order, ding, kou, is_mail, gender,
                    is_deleted, uuid, updated_at, deleted_at, version, device_id, sync_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            fillMemberFields(statement, member);
            statement.setInt(21, member.getDeletedAt() != null ? 1 : 0);
            statement.setString(22, member.getUuid());
            statement.setString(23, member.getUpdatedAt());
            statement.setString(24, member.getDeletedAt());
            statement.setObject(25, member.getVersion());
            statement.setString(26, member.getDeviceId());
            statement.setString(27, syncStatus);
            statement.executeUpdate();
        }
    }

    private void updateMemberByUuid(Connection connection, LightMember member, String syncStatus) throws SQLException {
        String sql = """
                UPDATE light_members SET
                    name = ?, phone = ?, city = ?, dist = ?, address = ?, zip_code = ?, birth_date = ?, lunar_birth_date = ?,
                    age = ?, zodiac = ?, zodiac_year = ?, birth_time = ?, note = ?, contact_person = ?, id_number = ?,
                    sort_order = ?, ding = ?, kou = ?, is_mail = ?, gender = ?, is_deleted = ?, updated_at = ?,
                    deleted_at = ?, version = ?, device_id = ?, sync_status = ?
                WHERE uuid = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            fillMemberFields(statement, member);
            statement.setInt(21, member.getDeletedAt() != null ? 1 : 0);
            statement.setString(22, member.getUpdatedAt());
            statement.setString(23, member.getDeletedAt());
            statement.setObject(24, member.getVersion());
            statement.setString(25, member.getDeviceId());
            statement.setString(26, syncStatus);
            statement.setString(27, member.getUuid());
            statement.executeUpdate();
        }
    }

    private void setMemberConflict(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE light_members SET sync_status = 'conflict' WHERE uuid = ?")) {
            statement.setString(1, uuid);
            statement.executeUpdate();
        }
    }

    private void insertDonation(Connection connection, Donation donation, String syncStatus) throws SQLException {
        String sql = """
                INSERT INTO donations (
                    member_id, receipt_no, donate_date, extra_no, amount, summary, donate_note, other_note, donor_no, light_no,
                    should_pay, donate_type, creator, is_deleted, uuid, updated_at, deleted_at, version, device_id, sync_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            fillDonationFields(statement, donation);
            statement.setInt(14, donation.getDeletedAt() != null ? 1 : 0);
            statement.setString(15, donation.getUuid());
            statement.setString(16, donation.getUpdatedAt());
            statement.setString(17, donation.getDeletedAt());
            statement.setObject(18, donation.getVersion());
            statement.setString(19, donation.getDeviceId());
            statement.setString(20, syncStatus);
            statement.executeUpdate();
        }
    }

    private void updateDonationByUuid(Connection connection, Donation donation, String syncStatus) throws SQLException {
        String sql = """
                UPDATE donations SET
                    member_id = ?, receipt_no = ?, donate_date = ?, extra_no = ?, amount = ?, summary = ?, donate_note = ?,
                    other_note = ?, donor_no = ?, light_no = ?, should_pay = ?, donate_type = ?, creator = ?,
                    is_deleted = ?, updated_at = ?, deleted_at = ?, version = ?, device_id = ?, sync_status = ?
                WHERE uuid = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            fillDonationFields(statement, donation);
            statement.setInt(14, donation.getDeletedAt() != null ? 1 : 0);
            statement.setString(15, donation.getUpdatedAt());
            statement.setString(16, donation.getDeletedAt());
            statement.setObject(17, donation.getVersion());
            statement.setString(18, donation.getDeviceId());
            statement.setString(19, syncStatus);
            statement.setString(20, donation.getUuid());
            statement.executeUpdate();
        }
    }

    private void setDonationConflict(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE donations SET sync_status = 'conflict' WHERE uuid = ?")) {
            statement.setString(1, uuid);
            statement.executeUpdate();
        }
    }

    private void fillMemberFields(PreparedStatement statement, LightMember member) throws SQLException {
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

    private void fillDonationFields(PreparedStatement statement, Donation donation) throws SQLException {
        statement.setObject(1, donation.getMemberId());
        statement.setString(2, donation.getReceiptNo());
        statement.setString(3, donation.getDonateDate());
        statement.setString(4, donation.getExtraNo());
        statement.setObject(5, donation.getAmount());
        statement.setString(6, donation.getSummary());
        statement.setString(7, donation.getDonateNote());
        statement.setString(8, donation.getOtherNote());
        statement.setString(9, donation.getDonorNo());
        statement.setString(10, donation.getLightNo());
        statement.setObject(11, donation.getShouldPay());
        statement.setString(12, donation.getDonateType());
        statement.setString(13, donation.getCreator());
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

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
