package tw.org.il.dongsheng.templeapp.sync;

public class DonationSupplementSyncRow {
    private Integer id;
    private Integer donationId;
    private String supplementDate;
    private String supplementNo;
    private String sourceType;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
    private Integer deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getDonationId() { return donationId; }
    public void setDonationId(Integer donationId) { this.donationId = donationId; }
    public String getSupplementDate() { return supplementDate; }
    public void setSupplementDate(String supplementDate) { this.supplementDate = supplementDate; }
    public String getSupplementNo() { return supplementNo; }
    public void setSupplementNo(String supplementNo) { this.supplementNo = supplementNo; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
