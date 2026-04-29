package tw.org.il.dongsheng.templeapp;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static String version = "Unknown";

    static {
        try (InputStream is = AppConfig.class.getResourceAsStream("/settings.properties")) {
            Properties prop = new Properties();
            if (is != null) {
                prop.load(is);
                version = prop.getProperty("app.version");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getVersion() {
        return version;
    }
}
