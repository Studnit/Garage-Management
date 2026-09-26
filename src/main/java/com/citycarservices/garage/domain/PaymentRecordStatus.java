package com.citycarservices.garage.domain;

public enum PaymentRecordStatus {
    RECEIVED("Received", "b-green"),
    PENDING_VERIFICATION("Pending verification", "b-amber"),
    REJECTED("Rejected", "b-red");

    private final String label;
    private final String css;

    PaymentRecordStatus(String label, String css) {
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
