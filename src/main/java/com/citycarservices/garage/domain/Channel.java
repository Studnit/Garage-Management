package com.citycarservices.garage.domain;

public enum Channel {
    EMAIL("Email"), SMS("SMS"), WHATSAPP("WhatsApp");

    private final String label;

    Channel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
