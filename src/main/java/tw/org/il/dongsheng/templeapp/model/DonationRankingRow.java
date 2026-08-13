package tw.org.il.dongsheng.templeapp.model;

public record DonationRankingRow(
        int rank,
        int memberId,
        String memberName,
        long totalAmount,
        int totalCount
) {
}
