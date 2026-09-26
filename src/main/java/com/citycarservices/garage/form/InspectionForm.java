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
public class InspectionForm {
    @Min(0)
    @Max(3000000)
    private Integer odometerReading;

    private List<Item> items = new ArrayList<>();

    @Size(max = 2000)
    private String notes;

    @Size(max = 1500)
    private String defectiveParts;

    @Size(max = 2000)
    private String recommendedRepairs;

    @DecimalMin("0.00")
    @Digits(integer = 9, fraction = 2)
    private BigDecimal estimatedCost;

    @Getter
    @Setter
    public static class Item {
        private InspectionArea area;
        private ItemCondition condition = ItemCondition.NOT_CHECKED;
        @Size(max = 500)
        private String remarks;
    }
}
