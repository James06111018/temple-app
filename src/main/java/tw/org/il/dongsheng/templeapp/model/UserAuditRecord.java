package tw.org.il.dongsheng.templeapp.model;

public class UserAuditRecord {
    private Integer id;
    private Integer userId;
    private String action;
    private String changedBy;
    private String changedAt;
    private String snapshot;

    public UserAuditRecord(Integer id, Integer userId, String action, String changedBy, String changedAt, String snapshot) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.snapshot = snapshot;
    }

    public Integer getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getChangedAt() {
        return changedAt;
    }

    public String getSnapshot() {
        return snapshot;
    }
}
