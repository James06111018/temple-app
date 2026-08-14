package tw.org.il.dongsheng.templeapp.model;

import java.time.LocalDate;

public record DonationDetailRow(
        int originalOrder,
        Integer donationId,
        String name,
        String address,
        String phone,
        String receiptNo,
        String donationDate,
        LocalDate donationDateValue,
        Integer amount,
        String summary,
        String category,
        String donationNote,
        Integer memberId,
        String memberIdText,
        String taiSuiLightNo,
        String brightLightNo,
        Integer amountDue,
        String operator,
        String description,
        String supplementNo
) {
}
