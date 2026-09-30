package tw.org.il.dongsheng.templeapp.util;

import org.junit.jupiter.api.Test;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberCalendarTest {

    @Test
    void fillsLunarBirthdayFromSolarBirthday() {
        LightMember member = new LightMember();
        member.setBirthDate("070.03.03");
        member.setBirthTime("午");

        MemberCalendar.populateMissingLunarBirthDate(member);
        MemberCalendar.populateDerivedFields(member);

        assertEquals("070.01.27", member.getLunarBirthDate());
        assertEquals("雞", member.getZodiac());
        assertEquals("辛酉", member.getZodiacYear());
    }

    @Test
    void earlyZiUsesFollowingDayButLateZiUsesBirthDay() {
        LightMember earlyZi = new LightMember();
        earlyZi.setBirthDate("070.03.03");
        earlyZi.setBirthTime("早子");

        LightMember lateZi = new LightMember();
        lateZi.setBirthDate("070.03.03");
        lateZi.setBirthTime("晚子");

        MemberCalendar.populateMissingLunarBirthDate(earlyZi);
        MemberCalendar.populateMissingLunarBirthDate(lateZi);

        assertEquals("070.01.28", earlyZi.getLunarBirthDate());
        assertEquals("070.01.27", lateZi.getLunarBirthDate());
    }
}
