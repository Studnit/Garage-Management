package com.citycarservices.garage.domain;

public enum ReminderType {
    NEXT_SERVICE_DATE("Next service due"),
    NEXT_SERVICE_KM("Service due by kilometres"),
    INSURANCE_EXPIRY("Insurance expiry"),
    PUC_EXPIRY("PUC expiry"),
    MAINTENANCE("Recommended maintenance");

    private final String label;

    ReminderType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
