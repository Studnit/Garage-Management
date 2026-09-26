package com.citycarservices.garage.domain;

public enum StockMovementType {
    IN("Stock in"), OUT("Stock out");

    private final String label;

    StockMovementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
