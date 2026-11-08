package br.com.bomgas.bina;

/** Container/on-container color pairs per tone. Every pair meets WCAG AA (see docs/ui-redesign.md). */
final class ToneColors {
    private ToneColors() {}

    static int container(StatusSummary.Tone tone) {
        switch (tone) {
            case OK: return R.color.status_ok_container;
            case ATTENTION: return R.color.status_attention_container;
            case PROBLEM: return R.color.status_problem_container;
            default: return R.color.status_neutral_container;
        }
    }

    static int onContainer(StatusSummary.Tone tone) {
        switch (tone) {
            case OK: return R.color.status_on_ok_container;
            case ATTENTION: return R.color.status_on_attention_container;
            case PROBLEM: return R.color.status_on_problem_container;
            default: return R.color.status_on_neutral_container;
        }
    }
}
