package com.citycarservices.garage.form;

import com.citycarservices.garage.domain.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class VehicleForm {
    private Long customerId;

    @NotBlank(message = "Vehicle number is required")
    @Size(max = 20)
    private String regNo;

    @NotBlank(message = "Make / brand is required")
    @Size(max = 60)
    private String make;

    @NotBlank(message = "Model is required")
    @Size(max = 80)
    private String model;

    @Size(max = 80)
    private String variant;

    @Min(value = 1980, message = "Year must be 1980 or later")
    @Max(value = 2100, message = "Enter a valid year")
    private Integer mfgYear;

    @NotNull(message = "Select the fuel type")
    private FuelType fuelType = FuelType.PETROL;

    @NotNull(message = "Odometer reading is required")
    @Min(value = 0, message = "Odometer cannot be negative")
    @Max(value = 3000000, message = "Enter a valid odometer reading")
    private Integer odometer = 0;

    @Size(max = 30)
    private String vin;

    @Size(max = 30)
    private String engineNo;

    @Size(max = 30)
    private String color;

    @Size(max = 100)
    private String insurer;

    @Size(max = 50)
    private String policyNo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate insuranceExpiry;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate pucExpiry;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate nextServiceDate;

    @Min(0)
    private Integer nextServiceKm;

    @Size(max = 1000)
    private String notes;

    public static VehicleForm of(Vehicle v) {
        VehicleForm f = new VehicleForm();
        f.customerId = v.getCustomer().getId();
        f.regNo = v.getRegNo();
        f.make = v.getMake();
        f.model = v.getModel();
        f.variant = v.getVariant();
        f.mfgYear = v.getMfgYear();
        f.fuelType = v.getFuelType();
        f.odometer = v.getOdometer();
        f.vin = v.getVin();
        f.engineNo = v.getEngineNo();
        f.color = v.getColor();
        f.insurer = v.getInsurer();
        f.policyNo = v.getPolicyNo();
        f.insuranceExpiry = v.getInsuranceExpiry();
        f.pucExpiry = v.getPucExpiry();
        f.nextServiceDate = v.getNextServiceDate();
        f.nextServiceKm = v.getNextServiceKm();
        f.notes = v.getNotes();
        return f;
    }
}
