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
public class BookingForm {
    private Long customerId;

    @NotNull(message = "Please select a vehicle")
    private Long vehicleId;

    private List<Long> serviceIds = new ArrayList<>();

    @NotNull(message = "Please choose a preferred date and time")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime preferredAt;

    private boolean pickupRequired;

    @Size(max = 500)
    private String pickupAddress;

    @Size(max = 1500)
    private String complaint;

    private BookingSource source = BookingSource.WALK_IN;
}
