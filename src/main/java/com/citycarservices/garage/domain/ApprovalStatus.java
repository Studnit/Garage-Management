package com.citycarservices.garage.domain;

public enum ApprovalStatus {
    DRAFT("Draft", "b-gray"),
    AWAITING("Awaiting approval", "b-amber"),
    APPROVED("Approved", "b-green"),
    REJECTED("Rejected", "b-red");

    private final String label;
    private final String css;

    ApprovalStatus(String label, String css) {
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
