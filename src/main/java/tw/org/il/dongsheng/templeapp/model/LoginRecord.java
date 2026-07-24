package tw.org.il.dongsheng.templeapp.model;

public class LoginRecord {
    private Integer id;
    private String loginDate;
    private String loginTime;
    private String computerName;
    private String operator;
    private String logoutDate;
    private String logoutTime;

    public LoginRecord(Integer id, String loginDate, String loginTime, String computerName, String operator, String logoutDate, String logoutTime) {
        this.id = id;
        this.loginDate = loginDate;
        this.loginTime = loginTime;
        this.computerName = computerName;
        this.operator = operator;
        this.logoutDate = logoutDate;
        this.logoutTime = logoutTime;
    }

    public Integer getId() {
        return id;
    }

    public String getLoginDate() {
        return loginDate;
    }

    public String getLoginTime() {
        return loginTime;
    }

    public String getComputerName() {
        return computerName;
    }

    public String getOperator() {
        return operator;
    }

    public String getLogoutDate() {
        return logoutDate;
    }

    public String getLogoutTime() {
        return logoutTime;
    }
}
