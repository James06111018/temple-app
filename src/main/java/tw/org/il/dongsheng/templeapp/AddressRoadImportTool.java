package tw.org.il.dongsheng.templeapp;

import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAddressRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.service.AddressRoadImportService;

import java.net.URI;
import java.nio.file.Path;

public class AddressRoadImportTool {
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].isBlank()) {
            System.out.println("Usage: ./gradlew importRoadData -ProadData=<csv-file-or-url>");
            return;
        }

        SQLiteAddressRepository repository = new SQLiteAddressRepository(SQLiteDatabaseManager.getInstance());
        AddressRoadImportService service = new AddressRoadImportService(repository);

        String source = args[0];
        int imported;
        if (source.startsWith("http://") || source.startsWith("https://")) {
            imported = service.importCsv(URI.create(source));
        } else {
            imported = service.importCsv(Path.of(source));
        }

        System.out.println("Imported address roads: " + imported);
    }
}
