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
public class JobLineForm {
    @NotNull
    private LineType lineType = LineType.LABOUR;

    @Size(max = 250)
    private String description;

    private Long partId;

    @NotNull(message = "Quantity / hours required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal unitPrice;

    @DecimalMin("0.00")
    @DecimalMax("28.00")
    private BigDecimal gstRate;

    @DecimalMin("0.00")
    @Digits(integer = 4, fraction = 2)
    private BigDecimal labourHours;
}
