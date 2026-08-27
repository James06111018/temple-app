package tw.org.il.dongsheng.templeapp.sync;

public class RoleFunctionSyncRow {
    private final String roleCode;
    private final String functionCode;

    public RoleFunctionSyncRow(String roleCode, String functionCode) {
        this.roleCode = roleCode;
        this.functionCode = functionCode;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getFunctionCode() {
        return functionCode;
    }
}
