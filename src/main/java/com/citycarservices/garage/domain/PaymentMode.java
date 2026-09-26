package com.citycarservices.garage.domain;

public enum PaymentMode {
    CASH("Cash"), UPI("UPI"), CARD("Card"), BANK_TRANSFER("Bank Transfer");

    private final String label;

    PaymentMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
