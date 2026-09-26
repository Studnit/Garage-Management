package com.citycarservices.garage.domain;

public enum MediaKind {
    PHOTO("Photo"), VIDEO("Video");

    private final String label;

    MediaKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
