package br.com.bomgas.bina;

import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class UpdatePolicy {
    private UpdatePolicy() {}
    public enum Decision { UPDATE, CURRENT, UNAVAILABLE, SUPPRESSED, ERROR }

    /** Null denotes an endpoint without a published version; failures are reported separately. */
    public static Decision decide(long installed, Long available, long offered, boolean manual) {
        if (available == null) return Decision.UNAVAILABLE;
        if (available <= 0) return Decision.ERROR;
        if (available <= installed) return Decision.CURRENT;
        return manual || shouldOffer(installed, available, offered) ? Decision.UPDATE : Decision.SUPPRESSED;
    }

    public static Decision check(long installed, long offered, boolean manual, VersionSource source) {
        try { return decide(installed, source.available(), offered, manual); }
        catch (Exception e) { return Decision.ERROR; }
    }

    public interface VersionSource { Long available() throws Exception; }

    public static boolean shouldOffer(long installed, long available, long offered) {
        return available > installed && available > offered;
    }
    public static boolean verify(InputStream input, String expected) throws IOException {
        if (expected == null || !expected.matches("[a-fA-F0-9]{64}")) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return hex.toString().equalsIgnoreCase(expected);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String endpoint(EnvironmentConfig environment) {
        return environment.baseUrl() + "/api/app/version?canal=" + (environment.local ? "dev" : "prod");
    }
}
