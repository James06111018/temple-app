package tw.org.il.dongsheng.templeapp.model;

public class DonationSupplement {
    private Integer id;
    private Integer donationId;
    private String supplementDate;
    private String supplementNo;
    private String sourceType;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
    private boolean deleted;

    public DonationSupplement() {
    }

    public DonationSupplement(
            Integer id,
            Integer donationId,
            String supplementDate,
            String supplementNo,
            String sourceType,
            String createdBy,
            String createdAt,
            String updatedBy,
            String updatedAt,
            boolean deleted
    ) {
        this.id = id;
        this.donationId = donationId;
        this.supplementDate = supplementDate;
        this.supplementNo = supplementNo;
        this.sourceType = sourceType;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDonationId() {
        return donationId;
    }

    public void setDonationId(Integer donationId) {
        this.donationId = donationId;
    }

    public String getSupplementDate() {
        return supplementDate;
    }

    public void setSupplementDate(String supplementDate) {
        this.supplementDate = supplementDate;
    }

    public String getSupplementNo() {
        return supplementNo;
    }

    public void setSupplementNo(String supplementNo) {
        this.supplementNo = supplementNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
