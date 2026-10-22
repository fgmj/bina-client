package br.com.bomgas.bina;

import org.junit.Test;
import static org.junit.Assert.*;

public class MonitoringStateTest {
    @Test public void missingPermissionsDoNotHideOtherRecoveryWarnings() {
        MonitoringState state = MonitoringState.assess(false, false, false, false, true);
        assertFalse(state.canStart);
        assertTrue(state.details.contains("READ_PHONE_STATE"));
        assertTrue(state.details.contains("READ_CALL_LOG"));
        assertTrue(state.details.contains("Habilite as notificações"));
        assertTrue(state.details.contains("Revise as restrições de bateria"));
    }
    @Test public void guidesRecoveryFromDeniedCallLogToRestartWithoutClaimingMissedEvents() {
        MonitoringState denied = MonitoringState.assess(true, false, true, true, false);
        assertFalse(denied.canStart);
        assertTrue(denied.details.contains("READ_CALL_LOG"));
        assertFalse(denied.details.contains("READ_PHONE_STATE"));

        MonitoringState stopped = MonitoringState.assess(true, true, false, true, false);
        assertTrue(stopped.canStart);
        assertEquals("Serviço de monitoramento parado", stopped.title);
        assertTrue(stopped.details.contains("Retomar serviço"));
        assertTrue(stopped.details.contains("forçar parada"));
        assertTrue(stopped.details.contains("não são reconstruídas"));

        MonitoringState resumed = MonitoringState.assess(true, true, true, true, false);
        assertEquals("Monitoramento ativo", resumed.title);
        assertTrue(resumed.details.contains("não confirma entrega"));
    }
    @Test public void stoppedServiceOffersRecoveryWithoutClaimingCaptureIsLost() {
        MonitoringState state = MonitoringState.assess(true, true, false, true, false);
        assertTrue(state.canStart);
        assertEquals("Serviço de monitoramento parado", state.title);
        assertTrue(state.details.contains("Retomar"));
        assertTrue(state.details.contains("broadcast"));
    }
    @Test public void activeServiceRemainsUsableWithNotificationsBlockedAndBatteryRestricted() {
        MonitoringState state = MonitoringState.assess(true, true, true, false, true);
        assertTrue(state.canStart);
        assertEquals("Monitoramento ativo com alertas", state.title);
        assertTrue(state.details.contains("notificações"));
        assertTrue(state.details.contains("bateria"));
    }
    @Test public void revokedPhonePermissionOverridesRunningService() {
        MonitoringState state = MonitoringState.assess(false, true, true, true, false);
        assertFalse(state.canStart);
        assertEquals("Monitoramento indisponível", state.title);
        assertTrue(state.details.contains("READ_PHONE_STATE"));
    }
}
