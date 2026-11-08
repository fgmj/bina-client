package br.com.bomgas.bina;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceLabelTest {
    @Test public void namesTheDeviceAndEnvironmentInPlainWords() {
        assertEquals("Recepção · Produção", DeviceLabel.describe("Recepção", true, false));
        assertEquals("Recepção (sincronizando) · Servidor local", DeviceLabel.describe("Recepção", false, true));
        assertEquals("Aparelho sem nome · Produção", DeviceLabel.describe("", false, false));
    }
}
