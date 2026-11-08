package br.com.bomgas.bina;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LocationQueueTest {
    private static final long MIN = 60_000;
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static LocationQueue.Point point(long at) {
        return LocationQueue.Point.of(-15.79, -47.88, 20f, "fused", at, 80);
    }

    private LocationQueue queue() throws Exception {
        return new LocationQueue(new File(temporary.getRoot(), "locations.json"));
    }

    @Test public void pointsCloserThanTheSamplingIntervalAreDiscarded() throws Exception {
        LocationQueue queue = queue();
        assertTrue(queue.offer(point(0)));
        assertFalse(queue.offer(point(2 * MIN)));
        assertTrue(queue.offer(point(5 * MIN)));
        assertEquals(2, queue.size());
    }

    @Test public void offlinePointsSurviveRestartAndKeepTheirIds() throws Exception {
        LocationQueue queue = queue();
        queue.offer(point(0));
        String id = queue.batch(10).get(0).pointId;
        LocationQueue restored = queue();
        assertEquals(1, restored.size());
        assertEquals(id, restored.batch(10).get(0).pointId);
        assertFalse("interval also holds across restarts", restored.offer(point(MIN)));
    }

    @Test public void batchesOldestFirstAndAcknowledgedPointsLeave() throws Exception {
        LocationQueue queue = queue();
        for (int i = 0; i < 5; i++) queue.offer(point(i * 5 * MIN));
        List<LocationQueue.Point> batch = queue.batch(2);
        assertEquals(2, batch.size());
        assertEquals(0, batch.get(0).recordedAt);
        queue.acknowledge(Arrays.asList(batch.get(0).pointId, batch.get(1).pointId));
        assertEquals(3, queue.size());
        assertEquals(10 * MIN, queue.batch(10).get(0).recordedAt);
        assertEquals(3, queue().size());
    }

    @Test public void capacityDropsTheOldestPoints() throws Exception {
        LocationQueue queue = queue();
        for (int i = 0; i < LocationQueue.CAPACITY + 3; i++) queue.offer(point(i * 5 * MIN));
        assertEquals(LocationQueue.CAPACITY, queue.size());
        assertEquals(3 * 5 * MIN, queue.batch(1).get(0).recordedAt);
    }

    @Test public void serverWithoutSupportPausesSendingButKeepsPoints() throws Exception {
        LocationQueue queue = queue();
        queue.offer(point(0));
        assertTrue(queue.canSend(1000));
        queue.markUnsupported(1000);
        assertFalse(queue.canSend(1000 + LocationQueue.UNSUPPORTED_RETRY_MS - 1));
        assertTrue(queue.canSend(1000 + LocationQueue.UNSUPPORTED_RETRY_MS));
        assertTrue(queue().unsupported());
        assertEquals(1, queue.size());
        queue.markSupported();
        assertTrue(queue.canSend(2000));
    }

    @Test public void corruptStorageIsSetAsideInsteadOfStoppingTracking() throws Exception {
        File file = new File(temporary.getRoot(), "locations.json");
        Files.write(file.toPath(), "garbage".getBytes(StandardCharsets.UTF_8));
        LocationQueue queue = new LocationQueue(file);
        assertEquals(0, queue.size());
        assertTrue(queue.offer(point(0)));
        assertTrue(new File(file + ".corrupt").exists());
    }
}
