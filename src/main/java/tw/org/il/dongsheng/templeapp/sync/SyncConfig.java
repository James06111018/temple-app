package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class SyncConfig {
    private static final String FILE_NAME = "sync.properties";
    private static final String DEFAULT_REMOTE_BASE_URL = "https://ep-hidden-hall-azu6xplg-pooler.c-3.ap-southeast-1.aws.neon.tech";
    private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://ep-hidden-hall-azu6xplg-pooler.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require";
    private static final String DEFAULT_USERNAME = "neondb_owner";

    private SyncConfig() {
    }

    public static Properties load() {
        Properties properties = new Properties();
        Path configFile = getConfigFile();

        try {
            if (Files.exists(configFile)) {
                try (InputStream inputStream = Files.newInputStream(configFile)) {
                    properties.load(inputStream);
                }
            } else {
                properties.setProperty("sync.remote.baseUrl", DEFAULT_REMOTE_BASE_URL);
                properties.setProperty("sync.remote.jdbcUrl", DEFAULT_JDBC_URL);
                properties.setProperty("sync.remote.username", DEFAULT_USERNAME);
                properties.setProperty("sync.remote.password", "");
                Files.createDirectories(configFile.getParent());
                try (OutputStream outputStream = Files.newOutputStream(configFile)) {
                    properties.store(outputStream, "Temple App sync settings");
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load sync settings.", ex);
        }

        return properties;
    }

    public static void save(Properties properties) {
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(configFile)) {
                properties.store(outputStream, "Temple App sync settings");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save sync settings.", ex);
        }
    }

    public static boolean hasRequiredSettings(Properties properties) {
        return properties != null
                && notBlank(properties.getProperty("sync.remote.jdbcUrl"))
                && notBlank(properties.getProperty("sync.remote.username"))
                && notBlank(properties.getProperty("sync.remote.password"));
    }

    public static Path getConfigFile() {
        return getConfigDir().resolve(FILE_NAME);
    }

    public static Path getConfigDir() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Paths.get(System.getenv("APPDATA"), "TempleApp");
        }
        if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "TempleApp");
        }
        return Paths.get(System.getProperty("user.home"), ".templeapp");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
