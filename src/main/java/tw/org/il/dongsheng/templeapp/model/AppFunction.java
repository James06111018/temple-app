package tw.org.il.dongsheng.templeapp.model;

public class AppFunction {
    private String functionCode;
    private String functionName;
    private boolean enabled;

    public AppFunction(String functionCode, String functionName, boolean enabled) {
        this.functionCode = functionCode;
        this.functionName = functionName;
        this.enabled = enabled;
    }

    public String getFunctionCode() {
        return functionCode;
    }

    public void setFunctionCode(String functionCode) {
        this.functionCode = functionCode;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
