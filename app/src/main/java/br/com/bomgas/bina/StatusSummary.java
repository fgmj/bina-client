package br.com.bomgas.bina;

/** Short, glanceable status shown on a card: a tone (never conveyed by color alone) and a label. */
public final class StatusSummary {
    public enum Tone { OK, NEUTRAL, ATTENTION, PROBLEM }

    public final Tone tone;
    public final String label;

    public StatusSummary(Tone tone, String label) {
        this.tone = tone;
        this.label = label;
    }
}
