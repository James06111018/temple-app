package tw.org.il.dongsheng.templeapp.model;

public class LightNumberRecord {
    private final Integer id;
    private final String managementType;
    private final String lightType;
    private final Integer serialNumber;
    private final Integer memberId;
    private final String principalName;
    private final String status;

    public LightNumberRecord(
            Integer id,
            String managementType,
            String lightType,
            Integer serialNumber,
            Integer memberId,
            String principalName,
            String status
    ) {
        this.id = id;
        this.managementType = managementType;
        this.lightType = lightType;
        this.serialNumber = serialNumber;
        this.memberId = memberId;
        this.principalName = principalName;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public String getManagementType() {
        return managementType;
    }

    public String getLightType() {
        return lightType;
    }

    public Integer getSerialNumber() {
        return serialNumber;
    }

    public Integer getMemberId() {
        return memberId;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public String getStatus() {
        return status;
    }

    public String getDisplayNumber() {
        return lightType + String.format("%05d", serialNumber);
    }

    public String getDisplayMemberId() {
        return memberId == null ? "" : String.format("%07d", memberId);
    }

    public String getDisplayStatus() {
        return switch (status) {
            case "A" -> "A - 已登記";
            case "D" -> "D - 已刪除";
            default -> "N - 未使用";
        };
    }
}
