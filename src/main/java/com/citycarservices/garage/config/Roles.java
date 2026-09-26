package com.citycarservices.garage.config;

/** SpEL expressions for @PreAuthorize, kept in one place. */
public final class Roles {

    public static final String MANAGEMENT = "hasAnyRole('SUPER_ADMIN','GARAGE_MANAGER')";
    public static final String ADVISORS = "hasAnyRole('SUPER_ADMIN','GARAGE_MANAGER','SERVICE_ADVISOR')";
    public static final String WORKSHOP = "hasAnyRole('SUPER_ADMIN','GARAGE_MANAGER','SERVICE_ADVISOR','MECHANIC')";
    public static final String BILLING = "hasAnyRole('SUPER_ADMIN','GARAGE_MANAGER','SERVICE_ADVISOR','ACCOUNTANT')";
    public static final String INVENTORY = "hasAnyRole('SUPER_ADMIN','GARAGE_MANAGER','INVENTORY_MANAGER')";
    public static final String ACCOUNTS = "hasAnyRole('SUPER_ADMIN','GARAGE_MANAGER','ACCOUNTANT')";

    private Roles() {
    }
}
