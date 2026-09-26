package com.citycarservices.garage.domain;

public enum JobStatus {
    OPEN("Open", "b-gray"),
    IN_PROGRESS("In progress", "b-orange"),
    ON_HOLD("On hold", "b-amber"),
    COMPLETED("Completed", "b-green");

    private final String label;
    private final String css;

    JobStatus(String label, String css) {
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
