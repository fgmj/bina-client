package br.com.bomgas.bina;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackingStatusTest {
    private static TrackingStatus.Inputs ready() {
        TrackingStatus.Inputs in = new TrackingStatus.Inputs();
        in.enabled = true;
        in.foregroundPermission = true;
        in.backgroundPermission = true;
        in.providerAvailable = true;
        in.lastPointTime = "14:05";
        return in;
    }

    @Test public void disabledTrackingIsNotShown() {
        TrackingStatus.Inputs in = ready();
        in.enabled = false;
        assertNull(TrackingStatus.summarize(in));
    }

    @Test public void healthyTrackingShowsTheLastPoint() {
        StatusSummary summary = TrackingStatus.summarize(ready());
        assertEquals(StatusSummary.Tone.OK, summary.tone);
        assertEquals("Ativa · último ponto 14:05", summary.label);
        TrackingStatus.Inputs first = ready();
        first.lastPointTime = null;
        assertEquals("Ativa · aguardando 1º ponto", TrackingStatus.summarize(first).label);
    }

    @Test public void blockersComeBeforeDegradations() {
        TrackingStatus.Inputs in = ready();
        in.foregroundPermission = false;
        in.serverUnsupported = true;
        assertEquals(StatusSummary.Tone.PROBLEM, TrackingStatus.summarize(in).tone);
        assertEquals("Sem permissão", TrackingStatus.summarize(in).label);

        in = ready();
        in.providerAvailable = false;
        assertEquals("Localização desligada no aparelho", TrackingStatus.summarize(in).label);
    }

    @Test public void serverWithoutSupportKeepsPointsAndSaysSo() {
        TrackingStatus.Inputs in = ready();
        in.serverUnsupported = true;
        in.pending = 12;
        StatusSummary summary = TrackingStatus.summarize(in);
        assertEquals(StatusSummary.Tone.ATTENTION, summary.tone);
        assertEquals("Aguardando suporte do servidor · 12 guardados", summary.label);
    }

    @Test public void withoutBackgroundPermissionOnlyWorksWithTheAppOpen() {
        TrackingStatus.Inputs in = ready();
        in.backgroundPermission = false;
        assertEquals(StatusSummary.Tone.ATTENTION, TrackingStatus.summarize(in).tone);
        assertEquals("Só com o app aberto", TrackingStatus.summarize(in).label);
    }

    @Test public void backlogIsReportedWhenOffline() {
        TrackingStatus.Inputs in = ready();
        in.pending = 4;
        assertEquals(StatusSummary.Tone.NEUTRAL, TrackingStatus.summarize(in).tone);
        assertEquals("4 aguardando envio", TrackingStatus.summarize(in).label);
    }
}
