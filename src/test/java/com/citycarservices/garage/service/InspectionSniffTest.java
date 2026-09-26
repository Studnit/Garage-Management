package com.citycarservices.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class InspectionSniffTest {

    @Test
    void recognisesRealImageAndVideoHeaders() {
        assertEquals("image/jpeg", InspectionService.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals("image/png", InspectionService.sniff(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("video/webm", InspectionService.sniff(new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}));
        assertEquals("video/mp4", InspectionService.sniff(new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}));
    }

    @Test
    void rejectsDisguisedFiles() {
        assertNull(InspectionService.sniff("<html><script>alert(1)</script>".getBytes()));
        assertNull(InspectionService.sniff(new byte[] {'M', 'Z', 0, 0}));
        assertNull(InspectionService.sniff(new byte[0]));
    }
}
