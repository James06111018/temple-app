package tw.org.il.dongsheng.templeapp.model;

public class AppUser {
    private Integer id;
    private String username;
    private String displayName;
    private String roleCode;
    private boolean enabled;

    public AppUser() {
    }

    public AppUser(Integer id, String username, String displayName, String roleCode, boolean enabled) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.enabled = enabled;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return displayName == null || displayName.isBlank() ? username : displayName;
    }
}
