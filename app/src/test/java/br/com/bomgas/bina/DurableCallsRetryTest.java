package br.com.bomgas.bina;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class DurableCallsRetryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final EnvironmentConfig local = EnvironmentConfig.local("localhost", "8080");
    private File file() { return new File(temporary.getRoot(), "calls"); }
    private void record(DurableCalls calls, String state, long wall, long elapsed, String boot,
            EnvironmentConfig destination) throws Exception {
        calls.record(state, 1, "", "line", wall, elapsed, boot, destination, "device", "{}");
    }
    private JsonObject body(DurableCalls calls, int index) {
        return JsonParser.parseString(calls.pending().get(index).json).getAsJsonObject();
    }

    @Test public void lostConfirmationRetriesIdenticalPayloadAfterPersistedBackoff() throws Exception {
        DurableCalls calls = new DurableCalls(file());
        record(calls, "RINGING", 1000, 10, "boot", local);
        Set<String> serverHistory = new HashSet<>();
        String original = calls.pending().get(0).json;
        calls.deliver(1000, (url, json) -> {
            serverHistory.add(JsonParser.parseString(json).getAsJsonObject().get("eventId").getAsString());
            throw new IOException("Confirmação perdida");
        });
        calls = new DurableCalls(file());
        assertEquals(1, calls.pending().get(0).attempts);
        assertEquals(16000, calls.pending().get(0).nextAttempt);
        calls.deliver(15999, (url, json) -> fail("Tentativa antecipada"));
        calls.deliver(16000, (url, json) -> {
            assertEquals(original, json);
            serverHistory.add(JsonParser.parseString(json).getAsJsonObject().get("eventId").getAsString());
        });
        assertEquals(1, serverHistory.size());
        assertTrue(new DurableCalls(file()).pending().isEmpty());
    }

    @Test public void cycleSurvivesRestartAndEnvironmentChangeWithOriginalDestination() throws Exception {
        DurableCalls calls = new DurableCalls(file());
        record(calls, "RINGING", 1000, 100, "boot", local);
        calls = new DurableCalls(file());
        record(calls, "OFFHOOK", 2000, 200, "boot", EnvironmentConfig.production());
        calls = new DurableCalls(file());
        record(calls, "IDLE", 14000, 12200, "boot", EnvironmentConfig.production());
        assertEquals(3, calls.pending().size());
        Set<String> eventIds = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            assertEquals(body(calls, 0).get("callId"), body(calls, i).get("callId"));
            assertEquals(local.eventsUrl(), calls.pending().get(i).url);
            assertEquals("android", body(calls, i).get("sourceType").getAsString());
            eventIds.add(body(calls, i).get("eventId").getAsString());
        }
        assertEquals(3, eventIds.size());
        JsonObject additional = JsonParser.parseString(body(calls, 2).get("additionalData").getAsString()).getAsJsonObject();
        assertEquals(12, additional.get("duration").getAsInt());
        assertEquals("", additional.get("numero").getAsString());
        calls.deliver(15000, (url, json) -> assertEquals(local.eventsUrl(), url));
        assertTrue(calls.pending().isEmpty());
    }

    @Test public void missedAndSuccessiveCallsHaveDifferentIdentityAndNoDuplicateBroadcasts() throws Exception {
        DurableCalls calls = new DurableCalls(file());
        record(calls, "RINGING", 1000, 100, "boot", local);
        record(calls, "RINGING", 1100, 110, "boot", local);
        record(calls, "IDLE", 2000, 200, "boot", local);
        calls = new DurableCalls(file());
        record(calls, "IDLE", 2100, 210, "boot", local);
        record(calls, "RINGING", 3000, 300, "boot", local);
        assertEquals(3, calls.pending().size());
        assertEquals("CALL_MISSED", body(calls, 1).get("eventType").getAsString());
        assertNotEquals(body(calls, 0).get("callId"), body(calls, 2).get("callId"));
    }

    @Test public void rebootDoesNotInventConversationDuration() throws Exception {
        DurableCalls calls = new DurableCalls(file());
        record(calls, "RINGING", 1000, 100, "old", local);
        record(calls, "OFFHOOK", 2000, 200, "old", local);
        calls = new DurableCalls(file());
        record(calls, "IDLE", 5000, 500, "new", local);
        JsonObject additional = JsonParser.parseString(body(calls, 2).get("additionalData").getAsString()).getAsJsonObject();
        assertFalse(additional.has("duration"));
    }

    @Test public void newRingAfterRebootDoesNotReuseAbandonedCall() throws Exception {
        DurableCalls calls = new DurableCalls(file());
        record(calls, "RINGING", 1000, 100, "old", local);
        calls = new DurableCalls(file());
        record(calls, "RINGING", 2000, 200, "new", local);
        assertEquals(2, calls.pending().size());
        assertNotEquals(body(calls, 0).get("callId"), body(calls, 1).get("callId"));
    }

    @Test public void processDeathDuringSendStillPreservesRetryBudget() throws Exception {
        DurableCalls calls = new DurableCalls(file());
        record(calls, "RINGING", 1000, 100, "boot", local);
        try {
            calls.deliver(1000, (url, json) -> { throw new IllegalStateException("Processo encerrado"); });
            fail("Simulação deve interromper o envio");
        } catch (IllegalStateException expected) { }
        calls = new DurableCalls(file());
        assertEquals(1, calls.pending().get(0).attempts);
        assertEquals(16000, calls.pending().get(0).nextAttempt);
    }
}
