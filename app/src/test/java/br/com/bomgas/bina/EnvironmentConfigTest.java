package br.com.bomgas.bina;

import org.junit.Test;
import static org.junit.Assert.*;

public class EnvironmentConfigTest {
    @Test public void localAddressAndPortAreValidatedBeforeSaving() {
        String[] invalidHosts = {"", "http://", "host/path", "host:8080", "user@host", "host?x=1", "host#part", "bad host", "ftp://host"};
        for (String host : invalidHosts) {
            assertThrows(host, IllegalArgumentException.class, () -> EnvironmentConfig.local(host, "8080"));
        }
        for (String port : new String[]{"", "0", "65536", "-1", "80/path", "abc"}) {
            assertThrows(IllegalArgumentException.class, () -> EnvironmentConfig.local("localhost", port));
        }
        assertEquals("https://dev.example:443/api/eventos", EnvironmentConfig.local("https://dev.example", "443").eventsUrl());
        assertEquals("http://[::1]:8080/api/eventos", EnvironmentConfig.local("[::1]", "8080").eventsUrl());
    }
    @Test public void switchingEnvironmentLeavesTheCapturedDestinationUnchanged() {
        EnvironmentConfig production = EnvironmentConfig.production();
        EnvironmentConfig local = EnvironmentConfig.local("192.168.1.10", "8080");
        assertEquals("https://bina.fernandojunior.com.br/api/eventos", production.eventsUrl());
        assertEquals("http://192.168.1.10:8080/api/eventos", local.eventsUrl());
        assertEquals("https://bina.fernandojunior.com.br/api/eventos", production.eventsUrl());
    }
}
