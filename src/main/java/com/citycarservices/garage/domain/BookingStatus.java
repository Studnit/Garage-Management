package com.citycarservices.garage.domain;

import java.util.List;

public enum BookingStatus {
    PENDING("Pending", "b-gray", "Booking received, awaiting confirmation"),
    CONFIRMED("Confirmed", "b-blue", "Appointment confirmed by City Car Services"),
    VEHICLE_RECEIVED("Vehicle Received", "b-indigo", "Vehicle received at the garage, job card opened"),
    INSPECTION("Inspection", "b-purple", "Mechanic is inspecting the vehicle"),
    IN_SERVICE("In Service", "b-orange", "Approved work is in progress"),
    QUALITY_CHECK("Quality Check", "b-amber", "Final quality check"),
    READY("Ready", "b-teal", "Vehicle is ready for delivery"),
    DELIVERED("Delivered", "b-green", "Vehicle handed over to the customer"),
    COMPLETED("Completed", "b-done", "Job closed and paid"),
    CANCELLED("Cancelled", "b-red", "Booking cancelled");

    public static final List<BookingStatus> FLOW = List.of(PENDING, CONFIRMED, VEHICLE_RECEIVED, INSPECTION,
            IN_SERVICE, QUALITY_CHECK, READY, DELIVERED, COMPLETED);

    private final String label;
    private final String css;
    private final String description;

    BookingStatus(String label, String css, String description) {
        this.label = label;
        this.css = css;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getCss() {
        return css;
    }

    public String getDescription() {
        return description;
    }

    /** Position in the normal flow (0-based); -1 for CANCELLED. */
    public int getStep() {
        return FLOW.indexOf(this);
    }

    public BookingStatus next() {
        int i = FLOW.indexOf(this);
        if (i < 0 || i >= FLOW.size() - 1) {
            return null;
        }
        return FLOW.get(i + 1);
    }

    public boolean isOpen() {
        return this != COMPLETED && this != CANCELLED;
    }

    /** True while the vehicle is physically at the garage. */
    public boolean isInGarage() {
        return this == VEHICLE_RECEIVED || this == INSPECTION || this == IN_SERVICE
                || this == QUALITY_CHECK || this == READY;
    }

    /** True once the job card exists. */
    public boolean isJobStarted() {
        return getStep() >= VEHICLE_RECEIVED.getStep() && this != CANCELLED;
    }
}
