package com.citycarservices.garage.domain;

public enum ReminderStatus {
    PENDING("Pending", "b-amber"),
    SENT("Sent", "b-green"),
    FAILED("Failed", "b-red"),
    DISMISSED("Dismissed", "b-gray");

    private final String label;
    private final String css;

    ReminderStatus(String label, String css) {
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
