package tw.org.il.dongsheng.templeapp.util;

import java.util.Map;

public final class LightTypeUtil {
    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "媽祖內殿燈", "內",
            "福德內殿燈", "永"
    );

    private LightTypeUtil() {
    }

    public static String abbreviation(String lightTypeName) {
        String name = lightTypeName == null ? "" : lightTypeName.trim();
        if (name.isEmpty()) {
            return "";
        }
        String configured = ABBREVIATIONS.get(name);
        if (configured != null) {
            return configured;
        }
        return name.substring(0, name.offsetByCodePoints(0, 1));
    }
}
