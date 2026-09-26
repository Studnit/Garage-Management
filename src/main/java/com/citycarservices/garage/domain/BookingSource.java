package com.citycarservices.garage.domain;

public enum BookingSource {
    ONLINE("Online"), WALK_IN("Walk-in"), PHONE("Phone");

    private final String label;

    BookingSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
