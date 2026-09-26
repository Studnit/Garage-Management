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
public class PaymentForm {
    @NotNull(message = "Enter the amount")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1")
    @Digits(integer = 9, fraction = 2)
    private BigDecimal amount;

    @NotNull(message = "Select the payment mode")
    private PaymentMode mode = PaymentMode.CASH;

    @Size(max = 100)
    private String reference;

    private boolean advance;

    @Size(max = 300)
    private String note;
}
