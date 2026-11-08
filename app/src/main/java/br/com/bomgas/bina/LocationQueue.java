package br.com.bomgas.bina;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Durable offline buffer of tracking points, independent of the call outbox.
 * Bounded: when full, the oldest points are dropped so tracking never stops.
 */
final class LocationQueue {
    static final long MIN_INTERVAL_MS = 4 * 60_000L + 30_000L;
    /** Seven days of one point every five minutes. */
    static final int CAPACITY = 7 * 24 * 12;
    static final long UNSUPPORTED_RETRY_MS = 6 * 60 * 60_000L;

    static final class Point {
        String pointId;
        double latitude, longitude;
        float accuracy;
        String provider;
        long recordedAt;
        int batteryLevel;

        static Point of(double latitude, double longitude, float accuracy, String provider,
                long recordedAt, int batteryLevel) {
            Point point = new Point();
            point.pointId = UUID.randomUUID().toString();
            point.latitude = latitude;
            point.longitude = longitude;
            point.accuracy = accuracy;
            point.provider = provider == null ? "" : provider;
            point.recordedAt = recordedAt;
            point.batteryLevel = batteryLevel;
            return point;
        }
    }

    private static final class State {
        List<Point> points = new ArrayList<>();
        long lastAcceptedAt = Long.MIN_VALUE;
        long unsupportedSince;
    }

    private final Gson gson = new Gson();
    private final File file, backup;
    private State state;

    /** java.io only: java.nio.file is unavailable before API 26 (minSdk is 24). */
    LocationQueue(File file) throws IOException {
        this.file = file;
        backup = new File(file + ".bak");
        // Same protocol as DurableCalls: a backup means the last replacement was interrupted.
        if (backup.exists()) {
            if (file.exists() && !file.delete()) throw new IOException("Falha ao recuperar localizações");
            if (!backup.renameTo(file)) throw new IOException("Falha ao restaurar localizações");
        }
        state = load();
    }

    private State load() throws IOException {
        if (!file.exists()) return new State();
        try (Reader input = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            State loaded = gson.fromJson(input, State.class);
            if (loaded == null || loaded.points == null) throw new IllegalStateException("incompleto");
            loaded.points.removeIf(point -> point == null || point.pointId == null);
            return loaded;
        } catch (RuntimeException e) {
            // Losing old tracking points is preferable to never tracking again; keep the file for support.
            File corrupt = new File(file + ".corrupt");
            if (corrupt.exists() && !corrupt.delete()) throw new IOException("Falha ao isolar localizações ilegíveis");
            if (!file.renameTo(corrupt)) throw new IOException("Falha ao isolar localizações ilegíveis");
            return new State();
        }
    }

    synchronized boolean offer(Point point) throws IOException {
        if (state.lastAcceptedAt != Long.MIN_VALUE && point.recordedAt - state.lastAcceptedAt < MIN_INTERVAL_MS
                && point.recordedAt >= state.lastAcceptedAt) return false;
        List<Point> before = new ArrayList<>(state.points);
        long lastBefore = state.lastAcceptedAt;
        state.points.add(point);
        while (state.points.size() > CAPACITY) state.points.remove(0);
        state.lastAcceptedAt = point.recordedAt;
        try {
            commit();
        } catch (IOException e) {
            // Memory must match disk: an unsaved point is neither sent nor counted for the interval.
            state.points = before;
            state.lastAcceptedAt = lastBefore;
            throw e;
        }
        return true;
    }

    synchronized List<Point> batch(int max) {
        return new ArrayList<>(state.points.subList(0, Math.min(max, state.points.size())));
    }

    synchronized void acknowledge(Collection<String> ids) throws IOException {
        Set<String> sent = new HashSet<>(ids);
        List<Point> before = new ArrayList<>(state.points);
        if (!state.points.removeIf(point -> sent.contains(point.pointId))) return;
        try {
            commit();
        } catch (IOException e) {
            state.points = before;
            throw e;
        }
    }

    synchronized int size() { return state.points.size(); }

    synchronized long lastAcceptedAt() { return state.lastAcceptedAt == Long.MIN_VALUE ? 0 : state.lastAcceptedAt; }

    synchronized void markUnsupported(long now) throws IOException {
        state.unsupportedSince = now;
        commit();
    }

    synchronized void markSupported() throws IOException {
        if (state.unsupportedSince == 0) return;
        state.unsupportedSince = 0;
        commit();
    }

    synchronized boolean unsupported() { return state.unsupportedSince != 0; }

    synchronized boolean canSend(long now) {
        return state.unsupportedSince == 0 || now - state.unsupportedSince >= UNSUPPORTED_RETRY_MS;
    }

    private void commit() throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Sem diretório para localizações");
        if (file.exists() && !file.renameTo(backup)) throw new IOException("Falha ao proteger localizações");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(gson.toJson(state).getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (IOException e) {
            if (file.exists() && !file.delete()) e.addSuppressed(new IOException("Falha ao remover gravação incompleta"));
            if (backup.exists() && !backup.renameTo(file)) e.addSuppressed(new IOException("Falha ao restaurar localizações"));
            throw e;
        }
        if (backup.exists() && !backup.delete()) throw new IOException("Falha ao confirmar localizações");
    }
}
