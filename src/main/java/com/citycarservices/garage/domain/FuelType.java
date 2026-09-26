package com.citycarservices.garage.domain;

public enum FuelType {
    PETROL("Petrol"), DIESEL("Diesel"), CNG("CNG"), LPG("LPG"), ELECTRIC("Electric"), HYBRID("Hybrid");

    private final String label;

    FuelType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
