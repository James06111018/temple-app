package tw.org.il.dongsheng.templeapp.model;

public record PeopleStatisticsRow(
        int memberId,
        String name,
        String address,
        String phone,
        String zipCode,
        String isMail,
        long totalAmount
) {
}
