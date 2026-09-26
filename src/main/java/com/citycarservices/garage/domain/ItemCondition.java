package com.citycarservices.garage.domain;

public enum ItemCondition {
    NOT_CHECKED("Not checked", "b-gray"),
    GOOD("Good", "b-green"),
    ATTENTION("Needs attention", "b-amber"),
    CRITICAL("Critical", "b-red");

    private final String label;
    private final String css;

    ItemCondition(String label, String css) {
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
