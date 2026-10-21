package br.com.bomgas.bina;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class UpdatePolicyTest {
    @Test public void offersOnlyHigherVersionCode() {
        assertTrue(UpdatePolicy.shouldOffer(12, 14, 0));
        assertFalse(UpdatePolicy.shouldOffer(14, 14, 0));
        assertFalse(UpdatePolicy.shouldOffer(15, 14, 0));
    }
    @Test public void remembersOfferedVersionAcrossChecks() {
        assertFalse(UpdatePolicy.shouldOffer(12, 14, 14));
        assertFalse(UpdatePolicy.shouldOffer(12, 13, 14));
        assertTrue(UpdatePolicy.shouldOffer(12, 15, 14));
    }
    @Test public void selectsChannelFromActiveEnvironment() {
        assertEquals(EnvironmentConfig.PRODUCTION_URL + "/api/app/version?canal=prod",
                UpdatePolicy.endpoint(EnvironmentConfig.production()));
        assertEquals("https://bina-dsv.bomgas.com.br:443/api/app/version?canal=dev",
                UpdatePolicy.endpoint(EnvironmentConfig.local("https://bina-dsv.bomgas.com.br", "443")));
        assertEquals("http://localhost:8080/api/app/version?canal=dev",
                UpdatePolicy.endpoint(EnvironmentConfig.local("localhost", "8080")));
    }
    @Test public void validatesKnownSha256AndRejectsCorruption() throws Exception {
        String hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertTrue(UpdatePolicy.verify(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), hash));
        assertFalse(UpdatePolicy.verify(new ByteArrayInputStream("abd".getBytes(StandardCharsets.UTF_8)), hash));
        assertFalse(UpdatePolicy.verify(new ByteArrayInputStream(new byte[0]), "bad"));
        assertFalse(UpdatePolicy.verify(new ByteArrayInputStream(new byte[0]), null));
    }
}
