package tw.org.il.dongsheng.templeapp.sync;

public class AuthUserSyncRow {
    private final Integer id;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final String roleCode;
    private final boolean enabled;
    private final String createdBy;
    private final String createdAt;
    private final String updatedBy;
    private final String updatedAt;

    public AuthUserSyncRow(Integer id, String username, String displayName, String passwordHash, String roleCode,
                           boolean enabled, String createdBy, String createdAt, String updatedBy, String updatedAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roleCode = roleCode;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
