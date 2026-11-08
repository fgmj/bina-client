package br.com.bomgas.bina;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Contract of the proposed POST /api/localizacoes (docs/api-needs.md) and how answers degrade. */
final class LocationUpload {
    enum Outcome { SENT, RETRY, UNSUPPORTED, DROP }
    static final int BATCH = 100;

    private LocationUpload() {}

    static String url(EnvironmentConfig environment) { return environment.baseUrl() + "/api/localizacoes"; }

    static Outcome classify(int httpCode) {
        if (httpCode >= 200 && httpCode < 300) return Outcome.SENT;
        if (httpCode == 404 || httpCode == 405 || httpCode == 501) return Outcome.UNSUPPORTED;
        if (httpCode == 400 || httpCode == 413 || httpCode == 422) return Outcome.DROP;
        return Outcome.RETRY;
    }

    static String payload(String sourceId, int versionCode, String versionName, List<LocationQueue.Point> points) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        JsonObject body = new JsonObject();
        body.addProperty("sourceType", "android");
        body.addProperty("sourceId", sourceId);
        body.addProperty("versionCode", versionCode);
        body.addProperty("versionName", versionName);
        JsonArray array = new JsonArray();
        for (LocationQueue.Point point : points) {
            JsonObject item = new JsonObject();
            item.addProperty("pointId", point.pointId);
            item.addProperty("latitude", point.latitude);
            item.addProperty("longitude", point.longitude);
            if (point.accuracy >= 0) item.addProperty("accuracy", point.accuracy);
            item.addProperty("provider", point.provider);
            item.addProperty("recordedAt", iso.format(new Date(point.recordedAt)));
            if (point.batteryLevel >= 0) item.addProperty("batteryLevel", point.batteryLevel);
            array.add(item);
        }
        body.add("points", array);
        return body.toString();
    }
}
