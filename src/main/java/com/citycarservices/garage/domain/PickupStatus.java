package com.citycarservices.garage.domain;

public enum PickupStatus {
    REQUESTED("Requested", "b-gray"),
    ASSIGNED("Driver assigned", "b-blue"),
    EN_ROUTE("On the way", "b-orange"),
    DONE("Done", "b-green"),
    CANCELLED("Cancelled", "b-red");

    private final String label;
    private final String css;

    PickupStatus(String label, String css) {
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
