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
public class FeedbackForm {
    @NotNull(message = "Please choose a rating")
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 1500)
    private String reviewText;
}
