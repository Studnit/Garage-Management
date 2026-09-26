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
public class ServiceItemForm {
    @NotBlank(message = "Service name is required")
    @Size(max = 120)
    private String name;

    @NotNull
    private ServiceCategory category = ServiceCategory.OTHER;

    @Size(max = 600)
    private String description;

    @Size(max = 10)
    private String icon;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 9, fraction = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("28.00")
    private BigDecimal gstRate = new BigDecimal("18.00");

    @DecimalMin("0.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal estimatedHours;

    @Min(0)
    private Integer intervalKm;

    @Min(0)
    private Integer intervalMonths;

    private boolean active = true;

    private int sortOrder;

    public static ServiceItemForm of(ServiceItem s) {
        ServiceItemForm f = new ServiceItemForm();
        f.name = s.getName();
        f.category = s.getCategory();
        f.description = s.getDescription();
        f.icon = s.getIcon();
        f.basePrice = s.getBasePrice();
        f.gstRate = s.getGstRate();
        f.estimatedHours = s.getEstimatedHours();
        f.intervalKm = s.getIntervalKm();
        f.intervalMonths = s.getIntervalMonths();
        f.active = s.isActive();
        f.sortOrder = s.getSortOrder();
        return f;
    }
}
