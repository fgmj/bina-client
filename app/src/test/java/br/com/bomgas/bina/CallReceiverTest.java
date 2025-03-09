package br.com.bomgas.bina;

import junit.framework.TestCase;

public class CallReceiverTest extends TestCase {

    public void testMontaURL() {
        assertEquals("http://192.1.2.3/number/61988881122", CallReceiver.montaURL("61988881122", "192.1.2.3", "http://%SAVED_IP%/number/%PHONE_NUMBER%"));
    }
}