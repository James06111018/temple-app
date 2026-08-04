package tw.org.il.dongsheng.templeapp.model;

import java.time.LocalDate;

public record LightRegistrationReportRow(
        String lightNumber,
        Integer memberId,
        String name,
        String gender,
        String birthDate,
        String lunarBirthDate,
        Integer age,
        String zodiac,
        String zodiacYear,
        String birthTime,
        String address,
        LocalDate registrationDate
) {
}
