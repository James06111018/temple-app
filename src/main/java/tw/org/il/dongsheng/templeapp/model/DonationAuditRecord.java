package tw.org.il.dongsheng.templeapp.model;

public record DonationAuditRecord(
        Integer id,
        Integer donationId,
        Integer memberId,
        String action,
        String changedBy,
        String changedAt,
        String memberName,
        Donation beforeDonation,
        Donation afterDonation,
        String reason
) {
}
