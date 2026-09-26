package com.citycarservices.garage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reminder")
@Getter
@Setter
public class Reminder extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 30)
    private ReminderType type;

    private LocalDate dueDate;
    private Integer dueKm;

    @Column(nullable = false, unique = true, length = 100)
    private String dedupeKey;

    @Column(nullable = false, length = 800)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.PENDING;

    /** Comma separated channels used e.g. "EMAIL,WHATSAPP". */
    @Column(length = 100)
    private String channelsUsed;

    private LocalDateTime sentAt;

    @Column(length = 500)
    private String result;
}
