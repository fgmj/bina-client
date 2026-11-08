package br.com.bomgas.bina;

/** Summary of the durable call outbox for the status card. Read-only view of the queue. */
public final class QueueStatus {
    private QueueStatus() {}

    public static StatusSummary summarize(int pending, boolean deliveryErrors, boolean storageFailure) {
        if (storageFailure) return new StatusSummary(StatusSummary.Tone.PROBLEM, "Falha na fila");
        if (pending == 0) return new StatusSummary(StatusSummary.Tone.OK, "Em dia");
        if (deliveryErrors) return new StatusSummary(StatusSummary.Tone.ATTENTION, pending + " pendentes");
        return new StatusSummary(StatusSummary.Tone.NEUTRAL, "Enviando " + pending);
    }
}
