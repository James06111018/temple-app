package tw.org.il.dongsheng.templeapp;

import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAddressRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.service.AddressVillageImportService;

public class AddressVillageImportTool {
    public static void main(String[] args) throws Exception {
        SQLiteAddressRepository repository = new SQLiteAddressRepository(SQLiteDatabaseManager.getInstance());
        AddressVillageImportService service = new AddressVillageImportService(repository);
        int imported = service.importFromNlsc();
        System.out.println("Imported address villages: " + imported);
    }
}
