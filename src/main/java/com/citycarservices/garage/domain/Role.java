package com.citycarservices.garage.domain;

public enum Role {
    SUPER_ADMIN("Super Admin"),
    GARAGE_MANAGER("Garage Manager"),
    SERVICE_ADVISOR("Service Advisor"),
    MECHANIC("Mechanic"),
    ACCOUNTANT("Accountant"),
    INVENTORY_MANAGER("Inventory Manager"),
    CUSTOMER("Customer");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isStaff() {
        return this != CUSTOMER;
    }

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
