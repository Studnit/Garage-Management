package com.citycarservices.garage.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FmtTest {

    private final Fmt fmt = new Fmt();

    @Test
    void groupsRupeesTheIndianWay() {
        assertEquals("0.00", fmt.plain(null));
        assertEquals("999.00", fmt.plain(new BigDecimal("999")));
        assertEquals("1,000.00", fmt.plain(new BigDecimal("1000")));
        assertEquals("1,23,456.50", fmt.plain(new BigDecimal("123456.5")));
        assertEquals("12,34,56,789.00", fmt.plain(new BigDecimal("123456789")));
        assertEquals("-1,500.00", fmt.plain(new BigDecimal("-1500")));
        assertEquals("₹2,360.00", fmt.money(new BigDecimal("2360")));
    }

    @Test
    void amountInWords() {
        assertEquals("Zero Rupees Only", fmt.words(BigDecimal.ZERO));
        assertEquals("One Thousand Two Hundred Thirty Four Rupees Only", fmt.words(new BigDecimal("1234")));
        assertEquals("Two Lakh Fifty Thousand Rupees Only", fmt.words(new BigDecimal("250000")));
        assertEquals("One Crore Rupees Only", fmt.words(new BigDecimal("10000000")));
        assertEquals("Five Hundred Rupees and Fifty Paise Only", fmt.words(new BigDecimal("500.50")));
    }

    @Test
    void quantityAndDates() {
        assertEquals("2", fmt.qty(new BigDecimal("2.00")));
        assertEquals("1.5", fmt.qty(new BigDecimal("1.50")));
        assertEquals("-", fmt.date(null));
        assertEquals("05 Jan 2026", fmt.date(LocalDate.of(2026, 1, 5)));
    }

    @Test
    void whatsAppLinkIsEncoded() {
        assertEquals("https://wa.me/919822012345?text=Hi%20there%21", fmt.waLink("98220 12345", "Hi there!"));
    }
}
