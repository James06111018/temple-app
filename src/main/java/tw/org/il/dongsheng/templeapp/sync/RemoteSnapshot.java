package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.util.List;

public class RemoteSnapshot {
    private final String nextToken;
    private final List<LightMember> members;
    private final List<Donation> donations;
    private final List<LightNumberSyncRow> lightNumbers;
    private final List<HouseholdLightSyncRow> householdLightRecords;
    private final List<DonationSupplementSyncRow> donationSupplements;

    public RemoteSnapshot(
            String nextToken,
            List<LightMember> members,
            List<Donation> donations,
            List<LightNumberSyncRow> lightNumbers,
            List<HouseholdLightSyncRow> householdLightRecords,
            List<DonationSupplementSyncRow> donationSupplements
    ) {
        this.nextToken = nextToken;
        this.members = members;
        this.donations = donations;
        this.lightNumbers = lightNumbers;
        this.householdLightRecords = householdLightRecords;
        this.donationSupplements = donationSupplements;
    }

    public String getNextToken() {
        return nextToken;
    }

    public List<LightMember> getMembers() {
        return members;
    }

    public List<Donation> getDonations() {
        return donations;
    }

    public List<LightNumberSyncRow> getLightNumbers() {
        return lightNumbers;
    }

    public List<HouseholdLightSyncRow> getHouseholdLightRecords() {
        return householdLightRecords;
    }

    public List<DonationSupplementSyncRow> getDonationSupplements() {
        return donationSupplements;
    }
}
