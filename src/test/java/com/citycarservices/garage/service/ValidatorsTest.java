package com.citycarservices.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ValidatorsTest {

    @Test
    void normalisesIndianMobileNumbers() {
        assertEquals("9822012345", Validators.mobile("+91 98220 12345"));
        assertEquals("9822012345", Validators.mobile("098220-12345"));
    }

    @Test
    void normalisesVehicleNumbers() {
        assertEquals("MH12AB1234", Validators.regNo("mh 12 ab-1234"));
    }

    @Test
    void blankBecomesNull() {
        assertNull(Validators.blankToNull("   "));
        assertEquals("x", Validators.blankToNull(" x "));
    }
}
