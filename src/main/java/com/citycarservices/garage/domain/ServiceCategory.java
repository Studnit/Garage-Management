package com.citycarservices.garage.domain;

public enum ServiceCategory {
    SERVICING("Servicing & Maintenance"),
    ENGINE("Engine"),
    BRAKES_CLUTCH("Brakes & Clutch"),
    SUSPENSION("Suspension & Steering"),
    ELECTRICAL_AC("Electrical, Electronics & AC"),
    TYRES_WHEELS("Tyres & Wheels"),
    BODY_PAINT("Body, Denting & Painting"),
    CLEANING("Washing, Cleaning & Detailing"),
    ASSISTANCE("Pickup, Drop & Assistance"),
    OTHER("Other Repairs");

    private final String label;

    ServiceCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
