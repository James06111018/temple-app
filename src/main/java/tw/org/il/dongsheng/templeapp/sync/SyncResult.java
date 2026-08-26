package tw.org.il.dongsheng.templeapp.sync;

public class SyncResult {
    private final boolean success;
    private final String message;

    public SyncResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
