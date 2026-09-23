package tw.org.il.dongsheng.templeapp.sync;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SyncTimestampTest {
    @Test
    void parsesPostgresOffsetWithFractionalSeconds() {
        assertEquals(
                LocalDateTime.of(2026, 9, 3, 1, 50, 21, 300_000_000),
                SyncTimestamp.parse("2026-09-03 01:50:21.3+00")
        );
    }

    @Test
    void parsesIsoOffsetAndNormalizesToUtc() {
        assertEquals(
                LocalDateTime.of(2026, 9, 3, 1, 50, 21),
                SyncTimestamp.parse("2026-09-03T09:50:21+08:00")
        );
    }

    @Test
    void parsesExistingSqliteFormats() {
        assertEquals(
                LocalDateTime.of(2026, 9, 3, 1, 50, 21),
                SyncTimestamp.parse("2026-09-03 01:50:21")
        );
        assertEquals(
                LocalDateTime.of(2026, 9, 3, 1, 50, 21, 123_000_000),
                SyncTimestamp.parse("2026-09-03 01:50:21.123")
        );
    }

    @Test
    void parsesRocDatesUsedByLegacyLightNumbers() {
        assertEquals(
                LocalDateTime.of(2025, 12, 30, 0, 0),
                SyncTimestamp.parse("114.12.30")
        );
        assertEquals(
                LocalDateTime.of(2025, 12, 30, 8, 15, 9),
                SyncTimestamp.parse("114/12/30 08:15:09")
        );
    }

    @Test
    void acceptsBlankValues() {
        assertNull(SyncTimestamp.parse(null));
        assertNull(SyncTimestamp.parse("  "));
    }
}
