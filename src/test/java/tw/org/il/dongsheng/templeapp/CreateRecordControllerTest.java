package tw.org.il.dongsheng.templeapp;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateRecordControllerTest {

    @Test
    void parsesRocDate() {
        assertEquals(LocalDate.of(2026, 5, 14), CreateRecordController.parseRocDate("115.05.14"));
    }

    @Test
    void parsesGregorianDate() {
        assertEquals(LocalDate.of(2026, 5, 14), CreateRecordController.parseRocDate("2026-05-14"));
    }

    @Test
    void rejectsIncompleteDate() {
        assertThrows(IllegalArgumentException.class,
                () -> CreateRecordController.parseRocDate("115.05"));
    }
}
