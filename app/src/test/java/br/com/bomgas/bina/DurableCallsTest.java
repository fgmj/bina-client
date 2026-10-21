package br.com.bomgas.bina;

import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import static org.junit.Assert.*;

public class DurableCallsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void restartPreservesOriginalEventAndAcknowledgement() throws Exception {
        File file = new File(temporary.getRoot(), "calls");
        DurableCalls calls = new DurableCalls(file);
        calls.record("RINGING", 1, "11999990000", "line", 1000, 100, "boot",
                EnvironmentConfig.local("localhost", "8080"), "device", "{}");
        String original = calls.pending().get(0).json;
        DurableCalls restored = new DurableCalls(file);
        assertEquals(original, restored.pending().get(0).json);
        assertEquals("1970-01-01T00:00:01.000Z", JsonParser.parseString(original)
                .getAsJsonObject().get("occurredAt").getAsString());
        restored.deliver(1000, (url, json) -> { assertEquals(original, json); });
        assertTrue(new DurableCalls(file).pending().isEmpty());
        restored.record("RINGING", 1, "11999990000", "line", 2000, 200, "boot",
                EnvironmentConfig.production(), "device", "{}");
        assertTrue(restored.pending().isEmpty());
    }
}
