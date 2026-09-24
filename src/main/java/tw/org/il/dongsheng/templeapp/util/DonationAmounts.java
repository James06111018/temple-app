package tw.org.il.dongsheng.templeapp.util;

import tw.org.il.dongsheng.templeapp.model.Donation;

public final class DonationAmounts {
    private DonationAmounts() {
    }

    /** Uses the actual received amount, with should_pay only as a legacy fallback. */
    public static int actual(Donation donation) {
        if (donation == null) {
            return 0;
        }
        if (donation.getAmount() != null) {
            return donation.getAmount();
        }
        return donation.getShouldPay() == null ? 0 : donation.getShouldPay();
    }
}
