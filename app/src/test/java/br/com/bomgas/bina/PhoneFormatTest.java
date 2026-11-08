package br.com.bomgas.bina;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneFormatTest {
    @Test public void formatsBrazilianMobileAndLandline() {
        assertEquals("(11) 98765-4321", PhoneFormat.display("11987654321"));
        assertEquals("(61) 3333-4444", PhoneFormat.display("6133334444"));
    }

    @Test public void keepsCountryCodeAndDropsTrunkPrefix() {
        assertEquals("+55 (11) 98765-4321", PhoneFormat.display("+5511987654321"));
        assertEquals("+55 (61) 3333-4444", PhoneFormat.display("556133334444"));
        assertEquals("(11) 98765-4321", PhoneFormat.display("011987654321"));
    }

    @Test public void tollFreeShortAndForeignNumbersStayReadable() {
        assertEquals("0800 123 4567", PhoneFormat.display("08001234567"));
        assertEquals("190", PhoneFormat.display("190"));
        assertEquals("+14155550100", PhoneFormat.display("+14155550100"));
    }

    @Test public void hiddenNumberIsNamedNotBlank() {
        assertEquals("Número oculto", PhoneFormat.display(""));
        assertEquals("Número oculto", PhoneFormat.display(null));
    }

    @Test public void spokenFormReadsDigitByDigit() {
        assertEquals("1 1 9 8 7", PhoneFormat.spoken("11987"));
        assertEquals("Número oculto", PhoneFormat.spoken(""));
    }
}
