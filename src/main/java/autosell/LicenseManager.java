package autosell;

public class LicenseManager {
    public static boolean isLicenseValid = true;

    public static String getHWID() {
        return "AUTOSELL_PRO_VIP";
    }

    public static boolean verify() {
        isLicenseValid = true;
        return true;
    }

    public static void syncSession(double d1, double d2, double d3, String s1, String s2, String s3) {
    }

    public static void updateClientState(double d1, double d2, double d3, String s1, String s2, String s3) {
    }
}
