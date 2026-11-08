package br.com.bomgas.bina;

import org.junit.Test;
import static org.junit.Assert.*;

public class StatusSummaryTest {
    @Test public void monitoringLevelTellsWhetherTheOperatorMustAct() {
        assertEquals(StatusSummary.Tone.OK, MonitoringState.assess(true, true, true, true, false).summary().tone);
        assertEquals("Ativo", MonitoringState.assess(true, true, true, true, false).summary().label);
        assertEquals(StatusSummary.Tone.ATTENTION, MonitoringState.assess(true, true, true, false, false).summary().tone);
        assertEquals(StatusSummary.Tone.ATTENTION, MonitoringState.assess(true, true, false, true, false).summary().tone);
        assertEquals("Parado", MonitoringState.assess(true, true, false, true, false).summary().label);
        StatusSummary blocked = MonitoringState.assess(false, true, true, true, false).summary();
        assertEquals(StatusSummary.Tone.PROBLEM, blocked.tone);
        assertEquals("Sem permissão", blocked.label);
    }

    @Test public void connectionTextPersistedBySettingsIsClassified() {
        assertEquals(StatusSummary.Tone.OK, ConnectionStatus.classify("Último teste: Conexão confirmada com Produção").tone);
        assertEquals(StatusSummary.Tone.OK, ConnectionStatus.classify("Último cadastro confirmado pelo servidor").tone);
        assertEquals("Conectado", ConnectionStatus.classify("Último cadastro confirmado pelo servidor").label);
        assertEquals(StatusSummary.Tone.PROBLEM, ConnectionStatus.classify("Último teste: Falha na conexão com Produção: timeout").tone);
        assertEquals(StatusSummary.Tone.PROBLEM, ConnectionStatus.classify("Falha no cadastro: HTTP 500").tone);
        assertEquals("Sem conexão", ConnectionStatus.classify("Falha no cadastro: HTTP 500").label);
        assertEquals("Testando…", ConnectionStatus.classify("Teste de conexão em andamento…").label);
        assertEquals(StatusSummary.Tone.NEUTRAL, ConnectionStatus.classify("Conexão ainda não testada").tone);
        assertEquals("Não testado", ConnectionStatus.classify(null).label);
    }

    @Test public void queueSummaryDistinguishesSendingFromStuck() {
        assertEquals(StatusSummary.Tone.OK, QueueStatus.summarize(0, false, false).tone);
        assertEquals("Em dia", QueueStatus.summarize(0, false, false).label);
        assertEquals(StatusSummary.Tone.NEUTRAL, QueueStatus.summarize(1, false, false).tone);
        assertEquals("Enviando 1", QueueStatus.summarize(1, false, false).label);
        assertEquals(StatusSummary.Tone.ATTENTION, QueueStatus.summarize(3, true, false).tone);
        assertEquals("3 pendentes", QueueStatus.summarize(3, true, false).label);
        assertEquals(StatusSummary.Tone.PROBLEM, QueueStatus.summarize(0, false, true).tone);
        assertEquals("Falha na fila", QueueStatus.summarize(0, false, true).label);
    }
}
