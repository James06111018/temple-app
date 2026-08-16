package tw.org.il.dongsheng.templeapp.model;

public record CreateRecord(
        long auditId,
        String date,
        String time,
        String status,
        String operator,
        String memberNumber,
        String name,
        String birthDate,
        String lunarBirthDate,
        String zodiac,
        String zodiacYear,
        String birthTime,
        String gender,
        String address,
        String phone,
        String zipCode,
        String isMail,
        String note,
        String category,
        String idNumber,
        String sortOrder
) {
}
