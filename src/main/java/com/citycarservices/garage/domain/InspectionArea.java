package com.citycarservices.garage.domain;

public enum InspectionArea {
    ENGINE("Engine", "Oil leaks, noise, smoke, belts, mounts"),
    BATTERY("Battery", "Voltage, terminals, health, charging"),
    BRAKES("Brakes", "Pads, discs, drums, fluid, handbrake"),
    TYRES("Tyres", "Tread depth, pressure, sidewalls, spare"),
    SUSPENSION("Suspension", "Shockers, bushes, links, steering play"),
    LIGHTS("Lights", "Headlamps, indicators, brake and tail lamps"),
    AC("AC", "Cooling, compressor, gas, filter, blower"),
    ELECTRICAL("Electrical System", "Wiring, fuses, sensors, warning lights, ECU codes"),
    FLUIDS("Fluids", "Engine oil, coolant, brake, clutch, wiper"),
    EXTERIOR("Exterior / Body", "Panels, paint, glass, wipers, mirrors"),
    INTERIOR("Interior", "Seats, dashboard, controls, upholstery"),
    ODOMETER("Odometer", "Reading verified, cluster working"),
    EXISTING_DAMAGE("Existing Damages", "Scratches, dents, cracks noted on arrival");

    private final String label;
    private final String hint;

    InspectionArea(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String getLabel() {
        return label;
    }

    public String getHint() {
        return hint;
    }
}
