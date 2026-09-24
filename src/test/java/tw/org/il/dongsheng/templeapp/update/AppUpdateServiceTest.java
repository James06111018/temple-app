package tw.org.il.dongsheng.templeapp.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppUpdateServiceTest {
    @Test
    void comparesNumericVersionSegments() {
        assertTrue(AppUpdateService.compareVersions("1.0.10", "1.0.9") > 0);
        assertTrue(AppUpdateService.compareVersions("v2.0.0", "1.99.99") > 0);
        assertEquals(0, AppUpdateService.compareVersions("1.0", "1.0.0"));
    }
}
