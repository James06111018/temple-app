package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.AppFunction;
import tw.org.il.dongsheng.templeapp.model.AppRole;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.MeritBoxOpening;
import tw.org.il.dongsheng.templeapp.model.MeritCategory;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteMeritBoxOpeningRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteMeritCategoryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteSyncStateRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SyncService {
    private final SQLiteLightMemberRepository lightMemberRepository;
    private final SQLiteDonationRepository donationRepository;
    private final SQLiteAuthRepository authRepository;
    private final SQLiteMeritCategoryRepository meritCategoryRepository;
    private final SQLiteMeritBoxOpeningRepository meritBoxOpeningRepository;
    private final SQLiteSyncStateRepository syncStateRepository;
    private final RemoteSyncGateway remoteSyncGateway;
    private final SyncState syncState;
    private final Properties syncProperties;
    private Consumer<String> progressListener = message -> { };
    private BooleanSupplier cancellationRequested = () -> false;

    public SyncService(
            SQLiteLightMemberRepository lightMemberRepository,
            SQLiteDonationRepository donationRepository,
            SQLiteAuthRepository authRepository,
            SQLiteMeritCategoryRepository meritCategoryRepository,
            SQLiteMeritBoxOpeningRepository meritBoxOpeningRepository,
            SQLiteSyncStateRepository syncStateRepository,
            RemoteSyncGateway remoteSyncGateway
    ) throws SQLException {
        this(lightMemberRepository, donationRepository, authRepository, meritCategoryRepository, meritBoxOpeningRepository, syncStateRepository, remoteSyncGateway, SyncConfig.load());
    }

    public SyncService(
            SQLiteLightMemberRepository lightMemberRepository,
            SQLiteDonationRepository donationRepository,
            SQLiteAuthRepository authRepository,
            SQLiteMeritCategoryRepository meritCategoryRepository,
            SQLiteMeritBoxOpeningRepository meritBoxOpeningRepository,
            SQLiteSyncStateRepository syncStateRepository,
            RemoteSyncGateway remoteSyncGateway,
            Properties syncProperties
    ) throws SQLException {
        this.lightMemberRepository = lightMemberRepository;
        this.donationRepository = donationRepository;
        this.authRepository = authRepository;
        this.meritCategoryRepository = meritCategoryRepository;
        this.meritBoxOpeningRepository = meritBoxOpeningRepository;
        this.syncStateRepository = syncStateRepository;
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
        this.remoteSyncGateway = remoteSyncGateway != null
                ? remoteSyncGateway
                : buildRemoteGateway(this.syncProperties);
    }

    public SyncResult syncNow() {
        SyncResult pushResult = push();
        if (!pushResult.isSuccess()) {
            return pushResult;
        }
        return pull();
    }

    /** Downloads a complete cloud snapshot without uploading the local snapshot. */
    public SyncResult downloadAllFromCloud() {
        return downloadAllFromCloud(null, null);
    }

    public SyncResult downloadAllFromCloud(Consumer<String> progressListener, BooleanSupplier cancellationRequested) {
        this.progressListener = progressListener == null ? message -> { } : progressListener;
        this.cancellationRequested = cancellationRequested == null ? () -> false : cancellationRequested;
        try {
            report("開始從雲端下載完整資料");
            SyncResult pullResult = pull(true);
            if (!pullResult.isSuccess()) {
                return pullResult;
            }
            report("雲端資料下載完成（未上傳任何本機資料）");
            return new SyncResult(true, "雲端資料下載完成，未上傳任何本機資料。");
        } catch (SyncCancelledException ignored) {
            report("同步已取消");
            return new SyncResult(false, "同步已取消。");
        } finally {
            this.progressListener = message -> { };
            this.cancellationRequested = () -> false;
        }
    }

    public SyncResult pull() {
        return pull(false);
    }

    private SyncResult pull(boolean fullSync) {
        try {
            if (remoteSyncGateway == null) {
                syncState.setLastPullAt(Util.nowUtc());
                syncState.setLastSyncAt(Util.nowUtc());
                syncStateRepository.save(syncState);
                return new SyncResult(true, "No remote gateway configured; local sync state updated.");
            }

            checkpoint();
            report("拉取雲端主要資料表...");
            RemoteSnapshot snapshot = remoteSyncGateway.fetchChanges(fullSync ? null : syncState.getLastSyncToken());
            if (snapshot == null) {
                syncState.setLastPullAt(Util.nowUtc());
                syncState.setLastSyncAt(Util.nowUtc());
                syncStateRepository.save(syncState);
                return new SyncResult(true, "Remote snapshot is empty.");
            }

            checkpoint();
            reportTable("合併", "light_members", snapshot.getMembers());
            applyRemoteMembers(snapshot.getMembers());
            checkpoint();
            reportTable("合併", "donations", snapshot.getDonations());
            applyRemoteDonations(snapshot.getDonations());
            checkpoint();
            reportTable("合併", "light_numbers", snapshot.getLightNumbers());
            applyRemoteLightNumbers(snapshot.getLightNumbers());
            checkpoint();
            reportTable("合併", "household_light_records", snapshot.getHouseholdLightRecords());
            applyRemoteHouseholdLightRecords(snapshot.getHouseholdLightRecords());
            checkpoint();
            reportTable("合併", "donation_supplements", snapshot.getDonationSupplements());
            applyRemoteDonationSupplements(snapshot.getDonationSupplements());
            checkpoint();
            report("合併權限資料表（app_roles、app_functions、role_functions、app_users）...");
            applyRemoteAuthTables();
            checkpoint();
            report("合併功德箱資料表（merit_categories、merit_box_openings）...");
            applyRemoteMeritTables();

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
            List<LightMember> membersToPush = loadDirtyMembers();
            List<Donation> donationsToPush = loadDirtyDonations();
            List<LightNumberSyncRow> lightNumbersToPush = loadChangedLightNumbers();
            List<HouseholdLightSyncRow> householdLightRecordsToPush = loadChangedHouseholdLightRecords();
            List<DonationSupplementSyncRow> donationSupplementsToPush = loadChangedDonationSupplements();
            if (remoteSyncGateway != null) {
                checkpoint();
                reportTable("上傳", "light_members", membersToPush);
                if (!membersToPush.isEmpty()) {
                    remoteSyncGateway.pushMembers(membersToPush);
                }
                checkpoint();
                reportTable("上傳", "donations", donationsToPush);
                if (!donationsToPush.isEmpty()) {
                    remoteSyncGateway.pushDonations(donationsToPush);
                }
                checkpoint();
                reportTable("上傳", "light_numbers", lightNumbersToPush);
                if (!lightNumbersToPush.isEmpty()) {
                    remoteSyncGateway.pushLightNumbers(lightNumbersToPush);
                }
                checkpoint();
                reportTable("上傳", "household_light_records", householdLightRecordsToPush);
                if (!householdLightRecordsToPush.isEmpty()) {
                    remoteSyncGateway.pushHouseholdLightRecords(householdLightRecordsToPush);
                }
                checkpoint();
                reportTable("上傳", "donation_supplements", donationSupplementsToPush);
                if (!donationSupplementsToPush.isEmpty()) {
                    remoteSyncGateway.pushDonationSupplements(donationSupplementsToPush);
                }
            }

            markMembersSynced(membersToPush);
            markDonationsSynced(donationsToPush);
            markLightNumbersSynced(lightNumbersToPush);
            markHouseholdLightRecordsSynced(householdLightRecordsToPush);
            markDonationSupplementsSynced(donationSupplementsToPush);

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

    private void report(String message) {
        progressListener.accept(message);
    }

    private void reportTable(String action, String table, List<?> rows) {
        report(action + " " + table + "（" + (rows == null ? 0 : rows.size()) + " 筆）...");
    }

    private void checkpoint() {
        if (cancellationRequested.getAsBoolean()) {
            throw new SyncCancelledException();
        }
    }

    private static final class SyncCancelledException extends RuntimeException {
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

    private void applyRemoteLightNumbers(List<LightNumberSyncRow> remoteRows) throws SQLException {
        if (remoteRows == null || remoteRows.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO light_numbers (
                    id, management_type, light_type, serial_number, member_id, principal_name, status,
                    created_by, created_at, updated_by, updated_at, registered_at, deleted_by, deleted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(management_type, light_type, serial_number) DO UPDATE SET
                    member_id = excluded.member_id,
                    principal_name = excluded.principal_name,
                    status = excluded.status,
                    created_by = excluded.created_by,
                    created_at = excluded.created_at,
                    updated_by = excluded.updated_by,
                    updated_at = excluded.updated_at,
                    registered_at = excluded.registered_at,
                    deleted_by = excluded.deleted_by,
                    deleted_at = excluded.deleted_at
                """;
        try (Connection connection = lightMemberRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            try {
                for (LightNumberSyncRow row : remoteRows) {
                    bindLightNumber(statement, row);
                    statement.executeUpdate();
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

    private void applyRemoteHouseholdLightRecords(List<HouseholdLightSyncRow> remoteRows) throws SQLException {
        if (remoteRows == null || remoteRows.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO household_light_records (
                    id, member_id, light_type_id, roc_year, light_no, note,
                    created_by, created_at, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(member_id, light_type_id, roc_year) DO UPDATE SET
                    light_no = excluded.light_no,
                    note = excluded.note,
                    created_by = excluded.created_by,
                    created_at = excluded.created_at,
                    updated_by = excluded.updated_by,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = donationRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            try {
                for (HouseholdLightSyncRow row : remoteRows) {
                    bindHouseholdLight(statement, row);
                    statement.executeUpdate();
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

    private void applyRemoteDonationSupplements(List<DonationSupplementSyncRow> remoteRows) throws SQLException {
        if (remoteRows == null || remoteRows.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO donation_supplements (
                    id, donation_id, supplement_date, supplement_no, source_type,
                    created_by, created_at, updated_by, updated_at, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(donation_id) DO UPDATE SET
                    supplement_date = excluded.supplement_date,
                    supplement_no = excluded.supplement_no,
                    source_type = excluded.source_type,
                    created_by = excluded.created_by,
                    created_at = excluded.created_at,
                    updated_by = excluded.updated_by,
                    updated_at = excluded.updated_at,
                    is_deleted = excluded.is_deleted
                """;
        try (Connection connection = donationRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            try {
                for (DonationSupplementSyncRow row : remoteRows) {
                    bindDonationSupplement(statement, row);
                    statement.executeUpdate();
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

    private List<MeritCategory> loadMeritCategories() throws SQLException {
        List<MeritCategory> categories = new ArrayList<>();
        String sql = "SELECT id, code, name, is_delete FROM merit_categories ORDER BY id";
        try (Connection connection = SQLiteDatabaseManager.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                categories.add(new MeritCategory(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getInt("is_delete") == 1
                ));
            }
        }
        return categories;
    }

    private List<MeritBoxOpening> loadMeritBoxOpenings() throws SQLException {
        List<MeritBoxOpening> openings = new ArrayList<>();
        String sql = """
                SELECT id, opening_date, serial_no, amount, opener, note, category_code,
                       category_name, created_by, created_at
                FROM merit_box_openings
                ORDER BY opening_date, id
                """;
        try (Connection connection = SQLiteDatabaseManager.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String createdAt = resultSet.getString("created_at");
                openings.add(new MeritBoxOpening(
                        resultSet.getLong("id"),
                        LocalDate.parse(resultSet.getString("opening_date")),
                        resultSet.getString("serial_no"),
                        resultSet.getLong("amount"),
                        resultSet.getString("opener"),
                        resultSet.getString("note"),
                        resultSet.getString("category_code"),
                        resultSet.getString("category_name"),
                        resultSet.getString("created_by"),
                        SyncTimestamp.parse(createdAt)
                ));
            }
        }
        return openings;
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

    private List<LightNumberSyncRow> loadChangedLightNumbers() throws SQLException {
        List<LightNumberSyncRow> rows = new ArrayList<>();
        String lastPushAt = syncState.getLastPushAt();
        if (lastPushAt == null || lastPushAt.isBlank()) {
            return rows;
        }
        String sql = "SELECT * FROM light_numbers WHERE updated_at > ?";
        try (Connection connection = lightMemberRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lastPushAt);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapLightNumber(resultSet));
                }
            }
        }
        return rows;
    }

    private List<HouseholdLightSyncRow> loadChangedHouseholdLightRecords() throws SQLException {
        List<HouseholdLightSyncRow> rows = new ArrayList<>();
        String lastPushAt = syncState.getLastPushAt();
        if (lastPushAt == null || lastPushAt.isBlank()) {
            return rows;
        }
        String sql = "SELECT * FROM household_light_records WHERE updated_at > ?";
        try (Connection connection = donationRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lastPushAt);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapHouseholdLight(resultSet));
                }
            }
        }
        return rows;
    }

    private List<DonationSupplementSyncRow> loadChangedDonationSupplements() throws SQLException {
        List<DonationSupplementSyncRow> rows = new ArrayList<>();
        String lastPushAt = syncState.getLastPushAt();
        if (lastPushAt == null || lastPushAt.isBlank()) {
            return rows;
        }
        String sql = "SELECT * FROM donation_supplements WHERE updated_at > ?";
        try (Connection connection = donationRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lastPushAt);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapDonationSupplement(resultSet));
                }
            }
        }
        return rows;
    }

    private List<AuthUserSyncRow> loadAuthUsers() throws SQLException {
        List<AuthUserSyncRow> users = new ArrayList<>();
        String sql = """
                SELECT id, username, display_name, password_hash, role_code, enabled,
                       created_by, created_at, updated_by, updated_at
                FROM app_users
                ORDER BY id
                """;
        try (Connection connection = authRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(new AuthUserSyncRow(
                        resultSet.getInt("id"),
                        resultSet.getString("username"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("role_code"),
                        resultSet.getInt("enabled") == 1,
                        resultSet.getString("created_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_by"),
                        resultSet.getString("updated_at")
                ));
            }
        }
        return users;
    }

    private List<AppRole> loadAuthRoles() throws SQLException {
        List<AppRole> roles = new ArrayList<>();
        String sql = "SELECT role_code, role_name FROM app_roles ORDER BY role_code";
        try (Connection connection = authRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                roles.add(new AppRole(resultSet.getString("role_code"), resultSet.getString("role_name")));
            }
        }
        return roles;
    }

    private List<AppFunction> loadAuthFunctions() throws SQLException {
        List<AppFunction> functions = new ArrayList<>();
        String sql = "SELECT function_code, function_name, enabled FROM app_functions ORDER BY function_code";
        try (Connection connection = authRepository.getDatabaseManager().getConnection();
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

    private List<RoleFunctionSyncRow> loadRoleFunctions() throws SQLException {
        List<RoleFunctionSyncRow> rows = new ArrayList<>();
        String sql = "SELECT role_code, function_code FROM role_functions ORDER BY role_code, function_code";
        try (Connection connection = authRepository.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new RoleFunctionSyncRow(
                        resultSet.getString("role_code"),
                        resultSet.getString("function_code")
                ));
            }
        }
        return rows;
    }

    private void applyRemoteAuthTables() throws SQLException {
        if (remoteSyncGateway == null) {
            return;
        }
        try {
            applyRemoteAuthRoles(remoteSyncGateway.fetchAppRoles());
        } catch (SQLException ex) {
            throw new SQLException("Pull failed at table app_roles: " + ex.getMessage(), ex);
        }
        try {
            applyRemoteAuthFunctions(remoteSyncGateway.fetchAppFunctions());
        } catch (SQLException ex) {
            throw new SQLException("Pull failed at table app_functions: " + ex.getMessage(), ex);
        }
        try {
            applyRemoteRoleFunctions(remoteSyncGateway.fetchRoleFunctions());
        } catch (SQLException ex) {
            throw new SQLException("Pull failed at table role_functions: " + ex.getMessage(), ex);
        }
        try {
            applyRemoteAuthUsers(remoteSyncGateway.fetchAppUsers());
        } catch (SQLException ex) {
            throw new SQLException("Pull failed at table app_users: " + ex.getMessage(), ex);
        }
    }

    private void applyRemoteAuthRoles(List<AppRole> roles) throws SQLException {
        try (Connection connection = authRepository.getDatabaseManager().getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     """
                     INSERT INTO app_roles (role_code, role_name)
                     VALUES (?, ?)
                     ON CONFLICT(role_code) DO UPDATE SET
                         role_name = excluded.role_name
                     """)) {
            connection.setAutoCommit(false);
            try {
                if (roles != null) {
                    for (AppRole role : roles) {
                        insert.setString(1, role.getRoleCode());
                        insert.setString(2, role.getRoleName());
                        insert.addBatch();
                    }
                    insert.executeBatch();
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

    private void applyRemoteAuthFunctions(List<AppFunction> functions) throws SQLException {
        try (Connection connection = authRepository.getDatabaseManager().getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     """
                     INSERT INTO app_functions (function_code, function_name, enabled)
                     VALUES (?, ?, ?)
                     ON CONFLICT(function_code) DO UPDATE SET
                         function_name = excluded.function_name,
                         enabled = excluded.enabled
                     """)) {
            connection.setAutoCommit(false);
            try {
                if (functions != null) {
                    for (AppFunction function : functions) {
                        insert.setString(1, function.getFunctionCode());
                        insert.setString(2, function.getFunctionName());
                        insert.setInt(3, function.isEnabled() ? 1 : 0);
                        insert.addBatch();
                    }
                    insert.executeBatch();
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

    private void applyRemoteRoleFunctions(List<RoleFunctionSyncRow> roleFunctions) throws SQLException {
        try (Connection connection = authRepository.getDatabaseManager().getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM role_functions");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO role_functions (role_code, function_code) VALUES (?, ?)")) {
            connection.setAutoCommit(false);
            try {
                connection.createStatement().execute("PRAGMA foreign_keys = OFF");
                delete.executeUpdate();
                if (roleFunctions != null) {
                    for (RoleFunctionSyncRow row : roleFunctions) {
                        insert.setString(1, row.getRoleCode());
                        insert.setString(2, row.getFunctionCode());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.createStatement().execute("PRAGMA foreign_keys = ON");
                connection.setAutoCommit(true);
            }
        }
    }

    private void applyRemoteAuthUsers(List<AuthUserSyncRow> users) throws SQLException {
        try (Connection connection = authRepository.getDatabaseManager().getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     """
                     INSERT INTO app_users (
                         id, username, display_name, password_hash, role_code, enabled,
                         created_by, created_at, updated_by, updated_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(username) DO UPDATE SET
                         display_name = excluded.display_name,
                         password_hash = excluded.password_hash,
                         role_code = excluded.role_code,
                         enabled = excluded.enabled,
                         created_by = excluded.created_by,
                         created_at = excluded.created_at,
                         updated_by = excluded.updated_by,
                         updated_at = excluded.updated_at
                     """)) {
            connection.setAutoCommit(false);
            try {
                if (users != null) {
                    for (AuthUserSyncRow user : users) {
                        insert.setObject(1, user.getId());
                        insert.setString(2, user.getUsername());
                        insert.setString(3, user.getDisplayName());
                        insert.setString(4, user.getPasswordHash());
                        insert.setString(5, user.getRoleCode());
                        insert.setInt(6, user.isEnabled() ? 1 : 0);
                        insert.setString(7, user.getCreatedBy());
                        insert.setString(8, user.getCreatedAt());
                        insert.setString(9, user.getUpdatedBy());
                        insert.setString(10, user.getUpdatedAt());
                        insert.addBatch();
                    }
                    insert.executeBatch();
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

    private void applyRemoteMeritTables() throws SQLException {
        if (remoteSyncGateway == null) {
            return;
        }
        try {
            List<MeritCategory> categories = remoteSyncGateway.fetchMeritCategories();
            meritCategoryRepository.replaceAll(categories);
        } catch (Exception ex) {
            throw new SQLException("Pull failed at table merit_categories (local replace): " + ex.getMessage(), ex);
        }
        try {
            List<MeritBoxOpening> openings = remoteSyncGateway.fetchMeritBoxOpenings();
            meritBoxOpeningRepository.replaceAll(openings);
        } catch (Exception ex) {
            throw new SQLException("Pull failed at table merit_box_openings (local replace): " + ex.getMessage(), ex);
        }
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

    private void markLightNumbersSynced(List<LightNumberSyncRow> rows) {
    }

    private void markHouseholdLightRecordsSynced(List<HouseholdLightSyncRow> rows) {
    }

    private void markDonationSupplementsSynced(List<DonationSupplementSyncRow> rows) {
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

    private void bindLightNumber(PreparedStatement statement, LightNumberSyncRow row) throws SQLException {
        statement.setObject(1, row.getId());
        statement.setString(2, row.getManagementType());
        statement.setString(3, row.getLightType());
        statement.setObject(4, row.getSerialNumber());
        statement.setObject(5, row.getMemberId());
        statement.setString(6, row.getPrincipalName());
        statement.setString(7, row.getStatus());
        statement.setString(8, row.getCreatedBy());
        statement.setString(9, row.getCreatedAt());
        statement.setString(10, row.getUpdatedBy());
        statement.setString(11, row.getUpdatedAt());
        statement.setString(12, row.getRegisteredAt());
        statement.setString(13, row.getDeletedBy());
        statement.setString(14, row.getDeletedAt());
    }

    private void bindHouseholdLight(PreparedStatement statement, HouseholdLightSyncRow row) throws SQLException {
        statement.setObject(1, row.getId());
        statement.setObject(2, row.getMemberId());
        statement.setObject(3, row.getLightTypeId());
        statement.setObject(4, row.getRocYear());
        statement.setString(5, row.getLightNo());
        statement.setString(6, row.getNote());
        statement.setString(7, row.getCreatedBy());
        statement.setString(8, row.getCreatedAt());
        statement.setString(9, row.getUpdatedBy());
        statement.setString(10, row.getUpdatedAt());
    }

    private void bindDonationSupplement(PreparedStatement statement, DonationSupplementSyncRow row) throws SQLException {
        statement.setObject(1, row.getId());
        statement.setObject(2, row.getDonationId());
        statement.setString(3, row.getSupplementDate());
        statement.setString(4, row.getSupplementNo());
        statement.setString(5, row.getSourceType());
        statement.setString(6, row.getCreatedBy());
        statement.setString(7, row.getCreatedAt());
        statement.setString(8, row.getUpdatedBy());
        statement.setString(9, row.getUpdatedAt());
        statement.setObject(10, row.getDeleted() == null ? 0 : row.getDeleted());
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

    private RemoteSyncGateway buildRemoteGateway(Properties properties) {
        if (!SyncConfig.hasRequiredSettings(properties)) {
            return null;
        }
        String jdbcUrl = properties.getProperty("sync.remote.jdbcUrl");
        String username = properties.getProperty("sync.remote.username");
        String password = properties.getProperty("sync.remote.password");
        return new PostgresRemoteSyncGateway(jdbcUrl, username, password);
    }
}
