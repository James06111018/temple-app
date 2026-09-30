package tw.org.il.dongsheng.templeapp.util;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 信眾生日的衍生資料。資料庫只保存生日與時辰；年齡、生肖、歲次一律於查詢時重算。
 */
public final class MemberCalendar {
    private MemberCalendar() {
    }

    public static void populateDerivedFields(LightMember member) {
        if (member == null) {
            return;
        }
        Lunar lunar = lunarFor(member).orElse(null);
        if (lunar == null) {
            member.setAge(null);
            member.setZodiac(null);
            member.setZodiacYear(null);
            return;
        }

        member.setAge(traditionalAge(lunar));
        member.setZodiac(toTraditional(lunar.getYearShengXiao()));
        member.setZodiacYear(lunar.getYearInGanZhi());
    }

    /**
     * 只在農曆生日空白時由國曆補入，避免覆寫舊系統已核對的農曆資料。
     * 早子（23:00–23:59）以隔日換算；晚子（00:00–00:59）維持當日。
     */
    public static void populateMissingLunarBirthDate(LightMember member) {
        if (member == null || !isBlank(member.getLunarBirthDate())) {
            return;
        }
        effectiveSolarDate(member.getBirthDate(), member.getBirthTime()).ifPresent(date -> {
            Lunar lunar = Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar();
            member.setLunarBirthDate(formatRocDate(lunar.getYear() - 1911, lunar.getMonth(), lunar.getDay()));
        });
    }

    public static Optional<Lunar> lunarFor(LightMember member) {
        if (member == null) {
            return Optional.empty();
        }
        Optional<Lunar> storedLunar = parseRocLunarDate(member.getLunarBirthDate());
        if (storedLunar.isPresent()) {
            return storedLunar;
        }
        return effectiveSolarDate(member.getBirthDate(), member.getBirthTime())
                .map(date -> Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar());
    }

    public static Integer traditionalAge(Lunar birthLunar) {
        if (birthLunar == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        int currentLunarYear = Solar.fromYmd(today.getYear(), today.getMonthValue(), today.getDayOfMonth())
                .getLunar().getYear();
        return currentLunarYear - birthLunar.getYear() + 1;
    }

    private static Optional<Lunar> parseRocLunarDate(String value) {
        if (isBlank(value)) {
            return Optional.empty();
        }
        try {
            String[] parts = value.trim().split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            return Optional.of(Lunar.fromYmd(
                    Integer.parseInt(parts[0]) + 1911,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> effectiveSolarDate(String rocDate, String birthTime) {
        if (isBlank(rocDate)) {
            return Optional.empty();
        }
        try {
            String[] parts = rocDate.trim().split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            LocalDate date = LocalDate.of(
                    Integer.parseInt(parts[0]) + 1911,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
            return Optional.of("早子".equals(birthTime == null ? "" : birthTime.trim()) ? date.plusDays(1) : date);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String formatRocDate(int rocYear, int month, int day) {
        return String.format("%03d.%02d.%02d", rocYear, month, day);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String toTraditional(String value) {
        return value == null ? null : value
                .replace("鼠", "鼠")
                .replace("牛", "牛")
                .replace("虎", "虎")
                .replace("兔", "兔")
                .replace("龙", "龍")
                .replace("蛇", "蛇")
                .replace("马", "馬")
                .replace("羊", "羊")
                .replace("猴", "猴")
                .replace("鸡", "雞")
                .replace("狗", "狗")
                .replace("猪", "豬");
    }
}
