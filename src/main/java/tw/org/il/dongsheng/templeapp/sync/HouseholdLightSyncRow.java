package tw.org.il.dongsheng.templeapp.sync;

public class HouseholdLightSyncRow {
    private Integer id;
    private Integer memberId;
    private Integer lightTypeId;
    private Integer rocYear;
    private String lightNo;
    private String note;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getMemberId() { return memberId; }
    public void setMemberId(Integer memberId) { this.memberId = memberId; }
    public Integer getLightTypeId() { return lightTypeId; }
    public void setLightTypeId(Integer lightTypeId) { this.lightTypeId = lightTypeId; }
    public Integer getRocYear() { return rocYear; }
    public void setRocYear(Integer rocYear) { this.rocYear = rocYear; }
    public String getLightNo() { return lightNo; }
    public void setLightNo(String lightNo) { this.lightNo = lightNo; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
