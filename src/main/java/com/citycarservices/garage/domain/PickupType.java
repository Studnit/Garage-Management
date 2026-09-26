package com.citycarservices.garage.domain;

public enum PickupType {
    PICKUP("Pickup"), DROP("Drop / Delivery");

    private final String label;

    PickupType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
