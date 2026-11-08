package br.com.bomgas.bina;

import android.content.Context;
import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Shared endpoint and verified APK download for automatic and explicit checks. */
final class UpdateClient {
    private static final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES)
            .followRedirects(false).followSslRedirects(false).build();

    private static final OkHttpClient metadataHttp = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build();

    static JsonObject fetch(EnvironmentConfig environment) throws IOException {
        String key = UpdatePolicy.endpoint(environment);
        JsonObject metadata;
        try (Response response = metadataHttp.newCall(new Request.Builder().url(key).build()).execute()) {
            // A channel without a published updater is normal, including production.
            if (response.code() == 404 || response.code() == 204) return null;
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            if (response.body().contentLength() > 65536) throw new IOException("Metadados grandes demais");
            try (InputStream stream = response.body().byteStream()) {
                byte[] bytes = readLimited(stream, 65536);
                metadata = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        return metadata;
    }

    static long code(JsonObject metadata) throws IOException {
        try {
            long code = metadata.get("versionCode").getAsBigDecimal().longValueExact();
            if (code <= 0 || metadata.get("versionName").getAsString().trim().isEmpty())
                throw new IllegalArgumentException();
            return code;
        } catch (RuntimeException e) { throw new IOException("Versão remota inválida", e); }
    }

    // Caller holds UpdateWorker.LOCK while touching the shared APK and persisted offer.
    static File download(Context context, EnvironmentConfig environment, JsonObject metadata,
            BooleanSupplier stopped) throws Exception {
        File partial = new File(context.getFilesDir(), "updates/download.part");
        String key = UpdatePolicy.endpoint(environment);
        long code = code(metadata);
        try {
            String hash = metadata.get("sha256").getAsString();
            if (!hash.matches("[a-fA-F0-9]{64}")) throw new IOException("SHA-256 inválido");
            HttpUrl origin = HttpUrl.get(environment.baseUrl());
            HttpUrl url = origin.resolve(metadata.get("url").getAsString());
            if (url == null || !url.scheme().equals(origin.scheme()) || !url.host().equals(origin.host()) || url.port() != origin.port()
                    || !url.username().isEmpty() || !url.password().isEmpty()) throw new IOException("URL de APK fora do servidor ativo");
            if (!partial.getParentFile().isDirectory() && !partial.getParentFile().mkdirs()) throw new IOException("Storage indisponível");
            try (Response download = http.newCall(new Request.Builder().url(url).build()).execute()) {
                if (!download.isSuccessful() || download.body() == null) throw new IOException("APK HTTP " + download.code());
                try (InputStream input = download.body().byteStream(); FileOutputStream output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[32768]; int count; long total = 0;
                    while ((count = input.read(buffer)) != -1) {
                        if (stopped.getAsBoolean()) throw new IOException("Download cancelado");
                        if ((total += count) > 256L * 1024 * 1024) throw new IOException("APK grande demais");
                        output.write(buffer, 0, count);
                    }
                    output.getFD().sync();
                }
            }
            UpdateApk.validate(context, partial, code, hash);
            if (!key.equals(UpdatePolicy.endpoint(new AppSettings(context).active()))) throw new IOException("Ambiente ativo alterado");
            File apk = new File(partial.getParentFile(), "update.apk");
            if (apk.exists() && !apk.delete()) throw new IOException("Não foi possível substituir APK");
            if (!partial.renameTo(apk)) throw new IOException("Não foi possível concluir APK");
            return apk;
        } finally { if (partial.exists()) partial.delete(); }
    }
    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > limit) throw new IOException("Metadados grandes demais");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
}
