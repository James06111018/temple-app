package tw.org.il.dongsheng.templeapp.repository.sqlite;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteCreateRecordRepositoryTest {

    @Test
    void updateSnapshotUsesAfterState() {
        String before = memberSnapshot(2, "舊姓名", "舊地址", "舊備註");
        String after = memberSnapshot(2, "新姓名", "新地址", "新備註");

        Map<String, String> values = SQLiteCreateRecordRepository.parseSnapshot(
                "before=" + before + System.lineSeparator() + "after=" + after);

        assertEquals("新姓名", values.get("name"));
        assertEquals("新地址", values.get("address"));
        assertEquals("新備註", values.get("note"));
    }

    @Test
    void snapshotKeepsCommaAndClosingBraceInText() {
        Map<String, String> values = SQLiteCreateRecordRepository.parseSnapshot(
                memberSnapshot(3, "王小明", "中山路1號, 2樓", "家屬共2人}已確認"));

        assertEquals("中山路1號, 2樓", values.get("address"));
        assertEquals("家屬共2人}已確認", values.get("note"));
    }

    @Test
    void reservedBlankNumberHasNoMemberSnapshot() {
        assertTrue(SQLiteCreateRecordRepository.parseSnapshot("reserved blank member id=5").isEmpty());
    }

    @Test
    void convertsTaipeiDayBoundaryToUtcDatabaseTime() {
        assertEquals("2026-05-13 16:00:00",
                SQLiteCreateRecordRepository.toDatabaseTime(LocalDate.of(2026, 5, 14)));
    }

    private static String memberSnapshot(int id, String name, String address, String note) {
        return "Member{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", phone='0912345678'" +
                ", city='宜蘭縣'" +
                ", dist='冬山鄉'" +
                ", address='" + address + '\'' +
                ", zipCode='269'" +
                ", birthDate='072.10.18'" +
                ", lunarBirthDate='072.09.09'" +
                ", age=44" +
                ", zodiac='豬'" +
                ", zodiacYear='癸亥'" +
                ", birthTime='吉'" +
                ", note='" + note + '\'' +
                ", contactPerson=''" +
                ", idNumber='A123456789'" +
                ", sortOrder=2" +
                ", ding=6" +
                ", kou=1" +
                ", isMail='N'" +
                ", gender='男'" +
                '}';
    }
}
