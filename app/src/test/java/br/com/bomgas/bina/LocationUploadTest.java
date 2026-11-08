package br.com.bomgas.bina;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class LocationUploadTest {
    @Test public void classifiesServerAnswersForGracefulDegradation() {
        assertEquals(LocationUpload.Outcome.SENT, LocationUpload.classify(200));
        assertEquals(LocationUpload.Outcome.SENT, LocationUpload.classify(202));
        assertEquals(LocationUpload.Outcome.UNSUPPORTED, LocationUpload.classify(404));
        assertEquals(LocationUpload.Outcome.UNSUPPORTED, LocationUpload.classify(405));
        assertEquals(LocationUpload.Outcome.UNSUPPORTED, LocationUpload.classify(501));
        assertEquals(LocationUpload.Outcome.DROP, LocationUpload.classify(400));
        assertEquals(LocationUpload.Outcome.DROP, LocationUpload.classify(413));
        assertEquals(LocationUpload.Outcome.DROP, LocationUpload.classify(422));
        assertEquals(LocationUpload.Outcome.RETRY, LocationUpload.classify(429));
        assertEquals(LocationUpload.Outcome.RETRY, LocationUpload.classify(503));
        assertEquals(LocationUpload.Outcome.RETRY, LocationUpload.classify(302));
    }

    @Test public void payloadFollowsTheProposedContract() {
        LocationQueue.Point point = LocationQueue.Point.of(-15.5, -47.25, 12.5f, "fused", 0, 55);
        JsonObject body = JsonParser.parseString(LocationUpload.payload("device-1", 17, "1.3.3",
                Collections.singletonList(point))).getAsJsonObject();
        assertEquals("android", body.get("sourceType").getAsString());
        assertEquals("device-1", body.get("sourceId").getAsString());
        assertEquals(17, body.get("versionCode").getAsInt());
        JsonObject sent = body.getAsJsonArray("points").get(0).getAsJsonObject();
        assertEquals(point.pointId, sent.get("pointId").getAsString());
        assertEquals(-15.5, sent.get("latitude").getAsDouble(), 0);
        assertEquals(-47.25, sent.get("longitude").getAsDouble(), 0);
        assertEquals(12.5, sent.get("accuracy").getAsDouble(), 0);
        assertEquals("fused", sent.get("provider").getAsString());
        assertEquals("1970-01-01T00:00:00.000Z", sent.get("recordedAt").getAsString());
        assertEquals(55, sent.get("batteryLevel").getAsInt());
    }

    @Test public void unknownBatteryIsOmitted() {
        LocationQueue.Point point = LocationQueue.Point.of(1, 2, 3f, "gps", 0, -1);
        JsonObject sent = JsonParser.parseString(LocationUpload.payload("d", 1, "1",
                Collections.singletonList(point))).getAsJsonObject().getAsJsonArray("points").get(0).getAsJsonObject();
        assertFalse(sent.has("batteryLevel"));
    }

    @Test public void endpointIsDerivedFromTheActiveEnvironment() {
        assertEquals(EnvironmentConfig.PRODUCTION_URL + "/api/localizacoes",
                LocationUpload.url(EnvironmentConfig.production()));
    }
}
