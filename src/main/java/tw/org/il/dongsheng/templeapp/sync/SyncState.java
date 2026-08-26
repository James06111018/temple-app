package tw.org.il.dongsheng.templeapp.sync;

public class SyncState {
    private String lastPullAt;
    private String lastPushAt;
    private String lastSyncAt;
    private String lastSyncToken;
    private String deviceId;
    private SyncConflictPolicy conflictPolicy = SyncConflictPolicy.LAST_WRITE_WINS;
    private String remoteBaseUrl;
    private String updatedAt;

    public String getLastPullAt() {
        return lastPullAt;
    }

    public void setLastPullAt(String lastPullAt) {
        this.lastPullAt = lastPullAt;
    }

    public String getLastPushAt() {
        return lastPushAt;
    }

    public void setLastPushAt(String lastPushAt) {
        this.lastPushAt = lastPushAt;
    }

    public String getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(String lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public String getLastSyncToken() {
        return lastSyncToken;
    }

    public void setLastSyncToken(String lastSyncToken) {
        this.lastSyncToken = lastSyncToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public SyncConflictPolicy getConflictPolicy() {
        return conflictPolicy;
    }

    public void setConflictPolicy(SyncConflictPolicy conflictPolicy) {
        this.conflictPolicy = conflictPolicy;
    }

    public String getRemoteBaseUrl() {
        return remoteBaseUrl;
    }

    public void setRemoteBaseUrl(String remoteBaseUrl) {
        this.remoteBaseUrl = remoteBaseUrl;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
