package br.com.bomgas.bina;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** One atomic snapshot owns both observed telephone state and the outbox. Single process owner. */
final class DurableCalls {
    interface Sender { void send(String url, String json) throws IOException; }
    static final class Pending {
        String id, url, json, error = "";
        int attempts;
        long nextAttempt;
    }
    private static final class Routing {
        String callId, url, sourceId, boot;
    }
    private static final class State {
        int version = 1;
        IncomingCallTracker tracker = new IncomingCallTracker();
        Map<Integer, Routing> routes = new HashMap<>();
        List<Pending> pending = new ArrayList<>();
    }
    private final Gson gson = new Gson();
    private final File file, backup;
    private final Object deliveryLock = new Object();
    private State state;

    DurableCalls(File file) throws IOException {
        this.file = file;
        backup = new File(file + ".bak");
        // A backup means the previous atomic replacement was interrupted before commit.
        if (backup.exists()) {
            if (file.exists() && !file.delete()) throw new IOException("Falha ao recuperar fila");
            if (!backup.renameTo(file)) throw new IOException("Falha ao restaurar fila");
        }
        if (!file.exists()) { state = new State(); return; }
        try (Reader input = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject persisted = JsonParser.parseReader(input).getAsJsonObject();
            for (String required : new String[]{"version", "tracker", "routes", "pending"}) {
                if (!persisted.has(required) || persisted.get(required).isJsonNull())
                    throw new IOException("Snapshot incompleto; dados preservados");
            }
            if (!persisted.getAsJsonObject("tracker").has("calls"))
                throw new IOException("Estado telefônico incompleto; dados preservados");
            state = gson.fromJson(persisted, State.class);
            if (state == null || state.version != 1 || state.pending == null || state.routes == null
                    || state.tracker == null) throw new IOException("Formato de fila inválido");
        } catch (RuntimeException e) { throw new IOException("Fila ilegível; dados preservados", e); }
    }

    private State copy() { return gson.fromJson(gson.toJson(state), State.class); }

    private void commit(State next) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Sem diretório para fila");
        if (file.exists() && !file.renameTo(backup)) throw new IOException("Falha ao proteger fila anterior");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(gson.toJson(next).getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (backup.exists() && !backup.delete()) throw new IOException("Falha ao confirmar gravação da fila");
            state = next;
        } catch (IOException e) {
            if (file.exists() && !file.delete()) e.addSuppressed(new IOException("Falha ao remover gravação incompleta"));
            if (backup.exists() && !backup.renameTo(file)) e.addSuppressed(new IOException("Falha ao restaurar fila anterior"));
            throw e;
        }
    }

    synchronized String record(String transition, int subscription, String number, String receiving,
            long occurredAt, long elapsed, String boot, EnvironmentConfig destination, String sourceId,
            String extraJson) throws IOException {
        State next = copy();
        IncomingCallTracker.Event event;
        Routing route = next.routes.get(subscription);
        if (transition.equals("RINGING") && route != null && !Objects.equals(boot, route.boot)) {
            // A new boot cannot prove an old observed call is still ringing. Leave its history incomplete.
            next.tracker.forget(subscription);
            next.routes.remove(subscription);
            route = null;
        }
        switch (transition) {
            case "RINGING":
                event = next.tracker.ringing(subscription, number, receiving);
                if (route == null && event != null) {
                    route = new Routing();
                    route.callId = UUID.randomUUID().toString();
                    route.url = destination.eventsUrl();
                    route.sourceId = sourceId;
                    route.boot = boot;
                    next.routes.put(subscription, route);
                }
                break;
            case "OFFHOOK":
                event = next.tracker.answered(subscription, elapsed);
                if (event != null && route != null) route.boot = boot;
                break;
            case "IDLE": event = next.tracker.ended(subscription, elapsed); break;
            default: throw new IllegalArgumentException("Transição desconhecida");
        }
        if (event == null) {
            // Persist an enriched repeat too, without generating another event.
            commit(next);
            return null;
        }
        if (route == null) throw new IOException("Ligação sem destino persistido");
        JsonObject additional = JsonParser.parseString(extraJson).getAsJsonObject();
        additional.addProperty("numero", event.phoneNumber);
        additional.addProperty("receivingNumber", event.receivingNumber);
        additional.addProperty("data", format(occurredAt, "dd/MM/yyyy HH:mm:ss", TimeZone.getDefault()));
        boolean knownDuration = !event.type.equals("CALL_ENDED")
                || (!"-1".equals(boot) && Objects.equals(boot, route.boot));
        if (knownDuration) {
            additional.addProperty("duration", event.durationSeconds);
            additional.addProperty("durationFormatted", duration(event.durationSeconds));
        }
        String description;
        switch (event.type) {
            case "CALL_RECEIVED": description = "Chamada recebida"; break;
            case "CALL_ANSWERED_INCOMING": description = "Chamada recebida atendida"; break;
            case "CALL_MISSED": description = "Chamada não atendida"; break;
            default: description = knownDuration ? "Chamada encerrada - Duração: " + duration(event.durationSeconds)
                    : "Chamada encerrada - duração indisponível após reinício";
        }
        Pending pending = new Pending();
        pending.id = UUID.randomUUID().toString();
        pending.url = route.url;
        JsonObject body = new JsonObject();
        body.addProperty("description", description);
        body.addProperty("deviceId", route.sourceId);
        body.addProperty("sourceType", "android");
        body.addProperty("sourceId", route.sourceId);
        body.addProperty("callId", route.callId);
        body.addProperty("eventId", pending.id);
        body.addProperty("occurredAt", format(occurredAt, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC")));
        body.addProperty("eventType", event.type);
        body.addProperty("additionalData", additional.toString());
        pending.json = body.toString();
        next.pending.add(pending);
        if (transition.equals("IDLE")) next.routes.remove(subscription);
        commit(next);
        return description;
    }

    synchronized List<Pending> pending() {
        List<Pending> result = new ArrayList<>();
        for (Pending item : state.pending) {
            Pending snapshot = new Pending();
            snapshot.id = item.id;
            snapshot.url = item.url;
            snapshot.json = item.json;
            snapshot.error = item.error;
            snapshot.attempts = item.attempts;
            snapshot.nextAttempt = item.nextAttempt;
            result.add(snapshot);
        }
        return result;
    }

    /** Only one delivery loop; the network runs outside the state lock so recording never waits on it. */
    void deliver(long now, Sender sender) throws IOException {
        synchronized (deliveryLock) { deliverBatch(now, sender); }
    }

    private void deliverBatch(long now, Sender sender) throws IOException {
        int processed = 0;
        Set<String> blocked = new HashSet<>();
        for (Pending item : pending()) {
            if (processed >= 20) break;
            if (blocked.contains(item.url)) continue;
            if (item.nextAttempt > now) { blocked.add(item.url); continue; }
            processed++;
            synchronized (this) {
                State next = copy();
                for (Pending current : next.pending) {
                    if (!current.id.equals(item.id)) continue;
                    current.attempts = Math.min(30, current.attempts + 1);
                    current.error = "Envio sem confirmação; será repetido com a mesma identidade";
                    current.nextAttempt = now + Math.min(3600000L, 15000L << Math.min(8, current.attempts - 1));
                    break;
                }
                // Reserve the attempt before any network I/O, including process death mid-request.
                commit(next);
            }
            String error = null;
            try { sender.send(item.url, item.json); }
            catch (IOException e) { error = e.getMessage() == null ? "Falha de rede" : e.getMessage(); }
            synchronized (this) {
                State next = copy();
                for (Iterator<Pending> iterator = next.pending.iterator(); iterator.hasNext();) {
                    Pending current = iterator.next();
                    if (!current.id.equals(item.id)) continue;
                    if (error == null) iterator.remove();
                    else {
                        current.error = error;
                        blocked.add(item.url);
                    }
                    break;
                }
                commit(next);
            }
        }
    }

    private static String format(long time, String pattern, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(zone);
        return format.format(new Date(time));
    }

    private static String duration(long seconds) {
        return seconds >= 3600 ? String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
