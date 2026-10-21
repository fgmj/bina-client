package br.com.bomgas.bina;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppSettingsTest {
    // Test APK storage: never clear a user's actual settings or contact production.
    private final Context context = InstrumentationRegistry.getInstrumentation().getContext();
    @Before public void reset() { context.getSharedPreferences("DeviceSettings", 0).edit().clear().commit(); }
    @After public void cleanup() { reset(); }

    @Test public void selectionAndStableIdentitySurviveNewInstances() {
        AppSettings settings = new AppSettings(context);
        assertEquals(EnvironmentConfig.production().eventsUrl(), settings.active().eventsUrl());
        String id = settings.sourceId();
        settings.select(EnvironmentConfig.local("192.168.1.10", "8081"));
        AppSettings reopened = new AppSettings(context);
        assertEquals("http://192.168.1.10:8081/api/eventos", reopened.active().eventsUrl());
        assertEquals(id, reopened.sourceId());
        reopened.select(EnvironmentConfig.production());
        assertEquals("192.168.1.10", new AppSettings(context).localAddress());
    }

    @Test public void namesRevisionsAndConnectionResultsBelongToOneServer() {
        AppSettings settings = new AppSettings(context);
        EnvironmentConfig local = EnvironmentConfig.local("localhost", "8080");
        EnvironmentConfig other = EnvironmentConfig.local("localhost", "8081");
        String token = settings.beginRegistration(local, "Recepção");
        settings.acceptRegistration(local, token, new ServerApi.Registration("Nome do servidor", 3));
        settings.connection(local, "Falha na conexão");
        AppSettings reopened = new AppSettings(context);
        assertEquals("Nome do servidor", reopened.name(local));
        assertEquals(Long.valueOf(3), reopened.revision(local));
        assertTrue(reopened.nameSynced(local));
        assertEquals("", reopened.name(EnvironmentConfig.production()));
        assertNull(reopened.revision(other));
        assertEquals("Conexão ainda não testada", reopened.connection(other));
    }

    @Test public void oldResponsesCannotReplaceMoreRecentEdits() {
        AppSettings settings = new AppSettings(context);
        EnvironmentConfig local = EnvironmentConfig.local("localhost", "8080");
        String old = settings.beginRegistration(local, "Antigo");
        String current = settings.beginRegistration(local, "Novo");
        settings.acceptRegistration(local, current, new ServerApi.Registration("Atual", 5));
        settings.acceptRegistration(local, old, new ServerApi.Registration("Antigo", 4));
        assertEquals("Atual", new AppSettings(context).name(local));
        assertEquals(Long.valueOf(5), settings.revision(local));
    }

    @Test public void unconfirmedNameRemainsSavedWithoutClaimingRegistration() {
        AppSettings settings = new AppSettings(context);
        EnvironmentConfig local = EnvironmentConfig.local("localhost", "8080");
        settings.beginRegistration(local, "Recepção offline");
        assertEquals("Recepção offline", new AppSettings(context).name(local));
        assertFalse(new AppSettings(context).nameSynced(local));
    }
}
