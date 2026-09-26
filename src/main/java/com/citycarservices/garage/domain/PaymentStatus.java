package com.citycarservices.garage.domain;

public enum PaymentStatus {
    UNPAID("Unpaid", "b-red"),
    PARTIAL("Partially paid", "b-amber"),
    PAID("Paid", "b-green");

    private final String label;
    private final String css;

    PaymentStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() {
        return label;
    }

    public String getCss() {
        return css;
    }
}
