package br.com.bomgas.bina;

/** Glanceable state of the optional location tracking, from Android observations. */
public final class TrackingStatus {
    public static final class Inputs {
        public boolean enabled, foregroundPermission, backgroundPermission, providerAvailable, serverUnsupported;
        public int pending;
        /** Local time of the last accepted point, or null before the first one. */
        public String lastPointTime;
    }

    private TrackingStatus() {}

    /** Null when tracking is off: the card is hidden instead of nagging. */
    public static StatusSummary summarize(Inputs in) {
        if (!in.enabled) return null;
        if (!in.foregroundPermission) return new StatusSummary(StatusSummary.Tone.PROBLEM, "Sem permissão");
        if (!in.providerAvailable)
            return new StatusSummary(StatusSummary.Tone.PROBLEM, "Localização desligada no aparelho");
        if (in.serverUnsupported) return new StatusSummary(StatusSummary.Tone.ATTENTION,
                "Aguardando suporte do servidor · " + in.pending + " guardados");
        if (!in.backgroundPermission) return new StatusSummary(StatusSummary.Tone.ATTENTION, "Só com o app aberto");
        if (in.pending > 1) return new StatusSummary(StatusSummary.Tone.NEUTRAL, in.pending + " aguardando envio");
        return new StatusSummary(StatusSummary.Tone.OK, in.lastPointTime == null
                ? "Ativa · aguardando 1º ponto" : "Ativa · último ponto " + in.lastPointTime);
    }
}
