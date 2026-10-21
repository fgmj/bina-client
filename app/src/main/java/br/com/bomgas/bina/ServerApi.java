package br.com.bomgas.bina;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Blocking HTTP boundary; callers run it off the Android main thread. */
public final class ServerApi {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();

    public static String validateName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Informe um nome de 1 a 255 caracteres, sem quebras de linha");
        }
        return name;
    }

    public static final class Registration {
        public final String name;
        public final long revision;
        Registration(String name, long revision) { this.name = name; this.revision = revision; }
    }

    public void testConnection(EnvironmentConfig destination) throws IOException {
        JsonElement result = execute(new Request.Builder().url(destination.sourcesUrl()).get().build());
        if (!result.isJsonArray()) throw new IOException("Resposta incompatível com o cadastro de origens");
    }

    public void sendEvent(EnvironmentConfig destination, String json, okhttp3.Callback callback) {
        Request request = new Request.Builder().url(destination.eventsUrl())
                .post(RequestBody.create(json, MediaType.get("application/json; charset=utf-8"))).build();
        client.newCall(request).enqueue(callback);
    }

    public Registration register(EnvironmentConfig destination, String sourceId, String name, Long revision) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("sourceType", "android");
        body.addProperty("sourceId", sourceId);
        body.addProperty("name", validateName(name));
        if (revision != null) body.addProperty("revision", revision);
        JsonElement result = execute(new Request.Builder().url(destination.sourcesUrl())
                .post(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8"))).build());
        try {
            JsonObject json = result.getAsJsonObject();
            String canonicalName = validateName(json.get("name").getAsString());
            if (!json.get("revision").isJsonPrimitive() || !json.getAsJsonPrimitive("revision").isNumber())
                throw new IllegalArgumentException();
            long canonicalRevision = json.get("revision").getAsBigDecimal().longValueExact();
            if (canonicalRevision < 1 || canonicalRevision > 9007199254740991L
                    || !"android".equals(json.get("sourceType").getAsString())
                    || !sourceId.equals(json.get("sourceId").getAsString())) throw new IllegalArgumentException();
            return new Registration(canonicalName, canonicalRevision);
        } catch (RuntimeException e) {
            throw new IOException("Cadastro retornado pelo servidor é inválido", e);
        }
    }

    private JsonElement execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Servidor respondeu HTTP " + response.code());
            if (response.body() == null) throw new IOException("Servidor respondeu sem conteúdo");
            try { return JsonParser.parseString(response.body().string()); }
            catch (RuntimeException e) { throw new IOException("Resposta inválida do servidor", e); }
        }
    }
}
