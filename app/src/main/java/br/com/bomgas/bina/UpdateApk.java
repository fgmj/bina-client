package br.com.bomgas.bina;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.util.*;

/** Rechecked immediately before handing a private APK to the installer. */
final class UpdateApk {
    static final class SignatureMismatch extends IOException {
        SignatureMismatch() { super("Assinatura diferente: APK debug e release não podem atualizar um ao outro. Use um APK assinado com a mesma chave."); }
    }
    static void validate(Context context, File apk, long code, String hash) throws Exception {
        try (InputStream input = new FileInputStream(apk)) {
            if (!UpdatePolicy.verify(input, hash)) throw new IOException("APK corrompido: SHA-256 divergente");
        }
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
        if (archive == null || !context.getPackageName().equals(archive.packageName)
                || version(archive) != code || code <= version(installed))
            throw new IOException("APK incompatível com este aplicativo ou versão");
        Signature[] candidate = signatures(archive);
        Signature[] current = signatures(installed);
        boolean compatible = candidate.length > 0 && current.length > 0;
        if (Build.VERSION.SDK_INT >= 28 && archive.signingInfo != null && !archive.signingInfo.hasMultipleSigners()
                && current.length == 1) {
            compatible = Arrays.asList(archive.signingInfo.getSigningCertificateHistory()).contains(current[0]);
        } else {
            compatible = compatible && new HashSet<>(Arrays.asList(candidate)).equals(new HashSet<>(Arrays.asList(current)));
        }
        if (!compatible) throw new SignatureMismatch();
    }
    private static Signature[] signatures(PackageInfo info) {
        Signature[] values = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                ? info.signingInfo.getApkContentsSigners() : info.signatures;
        return values == null ? new Signature[0] : values;
    }
    static long version(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
}
