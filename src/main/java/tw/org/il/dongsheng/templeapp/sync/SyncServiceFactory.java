package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteMeritBoxOpeningRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteMeritCategoryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteSyncStateRepository;

import java.util.Properties;

public final class SyncServiceFactory {
    private SyncServiceFactory() {
    }

    public static SyncService create(Properties properties) throws Exception {
        SQLiteDatabaseManager databaseManager = SQLiteDatabaseManager.getInstance();
        SQLiteAuthRepository authRepository = new SQLiteAuthRepository(databaseManager);
        SQLiteLightMemberRepository lightMemberRepository = new SQLiteLightMemberRepository(databaseManager);
        SQLiteDonationRepository donationRepository = new SQLiteDonationRepository(databaseManager);
        SQLiteMeritCategoryRepository meritCategoryRepository = new SQLiteMeritCategoryRepository(databaseManager);
        SQLiteMeritBoxOpeningRepository meritBoxOpeningRepository = new SQLiteMeritBoxOpeningRepository(databaseManager);
        SQLiteSyncStateRepository syncStateRepository = new SQLiteSyncStateRepository(databaseManager);

        authRepository.createTables();
        lightMemberRepository.createTable();
        donationRepository.createTable();
        syncStateRepository.createTable();

        return new SyncService(
                lightMemberRepository,
                donationRepository,
                authRepository,
                meritCategoryRepository,
                meritBoxOpeningRepository,
                syncStateRepository,
                null,
                properties
        );
    }
}
