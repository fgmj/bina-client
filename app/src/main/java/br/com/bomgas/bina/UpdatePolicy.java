package br.com.bomgas.bina;

import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class UpdatePolicy {
    private UpdatePolicy() {}
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
