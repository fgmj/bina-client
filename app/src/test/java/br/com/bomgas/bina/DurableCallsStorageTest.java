package br.com.bomgas.bina;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class DurableCallsStorageTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final EnvironmentConfig local = EnvironmentConfig.local("localhost", "8080");
    private void ring(DurableCalls calls, int subscription, long now) throws IOException {
        calls.record("RINGING", subscription, "11999990000", "line", now, now, "boot", local, "device", "{}");
    }

    @Test public void interruptedReplacementRestoresPreviousCommittedEvents() throws Exception {
        File file = new File(temporary.getRoot(), "calls");
        DurableCalls calls = new DurableCalls(file);
        ring(calls, 1, 1000);
        String original = calls.pending().get(0).json;
        Files.copy(file.toPath(), new File(file + ".bak").toPath());
        Files.write(file.toPath(), "incomplete write".getBytes(StandardCharsets.UTF_8));
        assertEquals(original, new DurableCalls(file).pending().get(0).json);
    }

    @Test public void invalidExistingStorageIsNotSilentlyCleared() throws Exception {
        File file = temporary.newFile("calls");
        Files.write(file.toPath(), "invalid".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> new DurableCalls(file));
        assertEquals("invalid", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void incompleteSnapshotCannotBeMistakenForAnEmptyQueue() throws Exception {
        File file = temporary.newFile("calls");
        Files.write(file.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> new DurableCalls(file));
    }

    @Test public void failedStorageDoesNotAdvanceObservedCycle() throws Exception {
        File parent = temporary.newFile("blocked");
        DurableCalls calls = new DurableCalls(new File(parent, "calls"));
        assertThrows(IOException.class, () -> ring(calls, 1, 1000));
        assertTrue(calls.pending().isEmpty());
        assertTrue(parent.delete());
        assertTrue(parent.mkdir());
        ring(calls, 1, 2000);
        assertEquals(1, calls.pending().size());
    }

    @Test public void retryDelayIsBoundedAndOtherDestinationsCanProgress() throws Exception {
        DurableCalls calls = new DurableCalls(new File(temporary.getRoot(), "calls"));
        ring(calls, 1, 1000);
        long now = 1000;
        long[] delays = {15000, 30000, 60000, 120000, 240000, 480000, 960000, 1920000, 3600000, 3600000};
        for (long delay : delays) {
            calls.deliver(now, (url, json) -> { throw new IOException("offline"); });
            assertEquals(now + delay, calls.pending().get(0).nextAttempt);
            now += delay;
        }
        calls.record("RINGING", 2, "", "line2", now, now, "boot",
                EnvironmentConfig.local("localhost", "8081"), "device", "{}");
        calls.deliver(now - 1, (url, json) -> assertEquals("http://localhost:8081/api/eventos", url));
        assertEquals(1, calls.pending().size());
    }

    @Test public void networkWaitDoesNotBlockRecordingAndAcceptedEventsStayRemoved() throws Exception {
        DurableCalls calls = new DurableCalls(new File(temporary.getRoot(), "calls"));
        ring(calls, 1, 1000);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch sending = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            Future<?> delivery = workers.submit(() -> {
                try {
                    calls.deliver(1000, (url, json) -> {
                        sending.countDown();
                        try { if (!release.await(15, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                        catch (InterruptedException e) { throw new IOException(e); }
                    });
                } catch (IOException e) { throw new RuntimeException(e); }
            });
            assertTrue(sending.await(15, TimeUnit.SECONDS));
            workers.submit(() -> { ring(calls, 2, 2000); return null; }).get(10, TimeUnit.SECONDS);
            assertEquals(2, calls.pending().size());
            release.countDown();
            delivery.get(15, TimeUnit.SECONDS);
            assertEquals(1, calls.pending().size());
            assertEquals(calls.pending().get(0).id,
                    new DurableCalls(new File(temporary.getRoot(), "calls")).pending().get(0).id);
        } finally { release.countDown(); workers.shutdownNow(); }
    }
}
