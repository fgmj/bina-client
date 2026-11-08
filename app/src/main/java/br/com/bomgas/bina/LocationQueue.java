package br.com.bomgas.bina;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    private final File file;
    private State state;

    LocationQueue(File file) throws IOException {
        this.file = file;
        state = load();
    }

    private State load() throws IOException {
        if (!file.exists()) return new State();
        try {
            State loaded = gson.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), State.class);
            if (loaded == null || loaded.points == null) throw new IllegalStateException("incompleto");
            loaded.points.removeIf(point -> point == null || point.pointId == null);
            return loaded;
        } catch (RuntimeException e) {
            // Losing old tracking points is preferable to never tracking again; keep the file for support.
            Files.move(file.toPath(), new File(file + ".corrupt").toPath(), StandardCopyOption.REPLACE_EXISTING);
            return new State();
        }
    }

    synchronized boolean offer(Point point) throws IOException {
        if (state.lastAcceptedAt != Long.MIN_VALUE && point.recordedAt - state.lastAcceptedAt < MIN_INTERVAL_MS
                && point.recordedAt >= state.lastAcceptedAt) return false;
        state.points.add(point);
        while (state.points.size() > CAPACITY) state.points.remove(0);
        state.lastAcceptedAt = point.recordedAt;
        commit();
        return true;
    }

    synchronized List<Point> batch(int max) {
        return new ArrayList<>(state.points.subList(0, Math.min(max, state.points.size())));
    }

    synchronized void acknowledge(Collection<String> ids) throws IOException {
        Set<String> sent = new HashSet<>(ids);
        if (state.points.removeIf(point -> sent.contains(point.pointId))) commit();
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
        File temporary = new File(file + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(gson.toJson(state).getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
