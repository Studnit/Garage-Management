package com.citycarservices.garage.domain;

public enum LineType {
    LABOUR("Labour"), PART("Part"), SERVICE("Service");

    private final String label;

    LineType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
