package br.com.bomgas.bina;

/** Classifies the free-text connection note persisted by SettingsController/AppSettings. */
public final class ConnectionStatus {
    private ConnectionStatus() {}

    public static StatusSummary classify(String connection) {
        String text = connection == null ? "" : connection;
        if (text.contains("em andamento")) return new StatusSummary(StatusSummary.Tone.NEUTRAL, "Testando…");
        if (text.startsWith("Último teste: Conexão confirmada") || text.startsWith("Último cadastro confirmado"))
            return new StatusSummary(StatusSummary.Tone.OK, "Conectado");
        if (text.startsWith("Último teste: Falha") || text.startsWith("Falha"))
            return new StatusSummary(StatusSummary.Tone.PROBLEM, "Sem conexão");
        return new StatusSummary(StatusSummary.Tone.NEUTRAL, "Não testado");
    }
}
