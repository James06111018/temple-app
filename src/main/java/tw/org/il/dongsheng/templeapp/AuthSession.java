package tw.org.il.dongsheng.templeapp;

import tw.org.il.dongsheng.templeapp.model.AppUser;

import java.util.HashSet;
import java.util.Set;

public final class AuthSession {
    private static AppUser currentUser;
    private static Integer loginRecordId;
    private static Set<String> functionCodes = new HashSet<>();

    private AuthSession() {
    }

    public static AppUser getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(AppUser user) {
        currentUser = user;
    }

    public static void setFunctionCodes(Set<String> codes) {
        functionCodes = codes == null ? new HashSet<>() : new HashSet<>(codes);
    }

    public static Integer getLoginRecordId() {
        return loginRecordId;
    }

    public static void setLoginRecordId(Integer id) {
        loginRecordId = id;
    }

    public static boolean hasFunction(String functionCode) {
        return functionCodes.contains(functionCode);
    }

    public static boolean isAdmin() {
        return currentUser != null && "ADMIN".equals(currentUser.getRoleCode());
    }

    public static boolean canManageSystem() {
        return hasFunction("SYSTEM_ADMIN");
    }

    public static String getCurrentOperatorName() {
        if (currentUser != null) {
            String operatorName = currentUser.toString();
            if (operatorName != null && !operatorName.isBlank()) {
                return operatorName;
            }
        }
        return System.getProperty("user.name", "");
    }
}
