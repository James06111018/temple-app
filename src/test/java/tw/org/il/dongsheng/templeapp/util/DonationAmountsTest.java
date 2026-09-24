package tw.org.il.dongsheng.templeapp.util;

import org.junit.jupiter.api.Test;
import tw.org.il.dongsheng.templeapp.model.Donation;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DonationAmountsTest {
    @Test
    void usesActualAmountBeforeLegacyShouldPay() {
        Donation donation = new Donation();
        donation.setAmount(1200);
        donation.setShouldPay(0);
        assertEquals(1200, DonationAmounts.actual(donation));
    }

    @Test
    void fallsBackToShouldPayWhenAmountIsMissing() {
        Donation donation = new Donation();
        donation.setShouldPay(500);
        assertEquals(500, DonationAmounts.actual(donation));
    }

    @Test
    void formatsDatabaseDateAsRocDate() {
        assertEquals("115.01.24", Util.convertToDbDateString(LocalDate.of(2026, 1, 24)));
    }
}
