package br.com.bomgas.bina;

/** "Recepção · Produção": who this device is and where it reports, in plain words. */
public final class DeviceLabel {
    private DeviceLabel() {}

    public static String describe(String name, boolean synced, boolean local) {
        String device = name == null || name.isEmpty() ? "Aparelho sem nome"
                : name + (synced ? "" : " (sincronizando)");
        return device + " · " + (local ? "Servidor local" : "Produção");
    }

    static String describe(AppSettings settings) {
        EnvironmentConfig active = settings.active();
        return describe(settings.name(active), settings.nameSynced(active), active.local);
    }
}
