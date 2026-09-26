package com.citycarservices.garage.domain;

public enum EstimateStatus {
    SENT("Sent to customer", "b-amber"),
    APPROVED("Approved", "b-green"),
    PARTIAL("Partially approved", "b-blue"),
    REJECTED("Rejected", "b-red");

    private final String label;
    private final String css;

    EstimateStatus(String label, String css) {
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
