package br.com.bomgas.bina;

/** Observed monitoring health, independent of the Activity and Android APIs. */
public final class MonitoringState {
    public final boolean canStart;
    public final String title;
    public final String details;

    private MonitoringState(boolean canStart, String title, String details) {
        this.canStart = canStart;
        this.title = title;
        this.details = details;
    }

    public static MonitoringState assess(boolean phoneState, boolean callLog, boolean running,
                                         boolean notifications, boolean backgroundRestricted) {
        String limits = "Após forçar parada, reabra o aplicativo pelo ícone. Chamadas não observadas "
                + "não são reconstruídas. Após reiniciar o aparelho, desbloqueie-o para permitir a recuperação. "
                + "Fabricantes podem exigir autoinício e ajuste de bateria. O serviço ativo não confirma entrega "
                + "ao servidor ou ao monitor; confira também a fila.";
        String details = "";
        if (!notifications) details += "Habilite as notificações do monitoramento nas configurações.\n";
        if (backgroundRestricted) details += "Revise as restrições de bateria do aplicativo.\n";
        if (!phoneState || !callLog) {
            String missing = (!phoneState ? "Telefone (READ_PHONE_STATE) " : "")
                    + (!callLog ? "Registro de chamadas (READ_CALL_LOG)" : "");
            return new MonitoringState(false, "Monitoramento indisponível",
                    "Permissões faltantes: " + missing.trim() + ". Abra as permissões do aplicativo.\n\n"
                            + details + "\n" + limits);
        }
        String title = !running ? "Serviço de monitoramento parado"
                : details.isEmpty() ? "Monitoramento ativo" : "Monitoramento ativo com alertas";
        if (!running) details = "Retomar serviço solicita nova inicialização com o app aberto. "
                + "A captura por broadcast e a entrega da fila são independentes do serviço e da tela principal.\n" + details;
        return new MonitoringState(true, title, details + "\n" + limits);
    }
}
