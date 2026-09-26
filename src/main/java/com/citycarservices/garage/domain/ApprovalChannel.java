package com.citycarservices.garage.domain;

public enum ApprovalChannel {
    PORTAL("Customer portal"), PHONE("Phone call"), IN_PERSON("In person"), WHATSAPP("WhatsApp");

    private final String label;

    ApprovalChannel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
