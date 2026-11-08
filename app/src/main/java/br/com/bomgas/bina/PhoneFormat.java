package br.com.bomgas.bina;

/** Display-only formatting of caller numbers. Never used for what is recorded or delivered. */
public final class PhoneFormat {
    public static final String HIDDEN = "Número oculto";

    private PhoneFormat() {}

    public static String display(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return HIDDEN;
        boolean plus = raw.trim().startsWith("+");
        if (digits.startsWith("0800") && digits.length() == 11)
            return "0800 " + digits.substring(4, 7) + " " + digits.substring(7);
        if ((plus || digits.length() >= 12) && digits.startsWith("55")
                && (digits.length() == 12 || digits.length() == 13)) {
            return "+55 " + national(digits.substring(2));
        }
        if (plus) return "+" + digits;
        if (digits.startsWith("0") && (digits.length() == 11 || digits.length() == 12))
            return national(digits.substring(1));
        if (digits.length() == 10 || digits.length() == 11) return national(digits);
        return digits;
    }

    private static String national(String digits) {
        String area = digits.substring(0, 2);
        String local = digits.substring(2);
        int split = local.length() - 4;
        return "(" + area + ") " + local.substring(0, split) + "-" + local.substring(split);
    }

    /** Digit-by-digit text for screen readers, so "11987" is not read as eleven thousand. */
    public static String spoken(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9+]", "");
        if (digits.isEmpty()) return HIDDEN;
        StringBuilder spoken = new StringBuilder();
        for (char c : digits.toCharArray()) {
            if (spoken.length() > 0) spoken.append(' ');
            spoken.append(c);
        }
        return spoken.toString();
    }
}
