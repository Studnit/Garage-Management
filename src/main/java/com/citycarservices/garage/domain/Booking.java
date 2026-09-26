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
@Table(name = "booking")
@Getter
@Setter
public class Booking extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String bookingNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToMany
    @JoinTable(name = "booking_service",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    private List<ServiceItem> services = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime preferredAt;

    private boolean pickupRequired;

    @Column(length = 500)
    private String pickupAddress;

    @Column(length = 1500)
    private String complaint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingSource source = BookingSource.ONLINE;

    @Column(length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC, id ASC")
    private List<BookingStatusLog> logs = new ArrayList<>();

    @OneToOne(mappedBy = "booking")
    private JobCard jobCard;

    public String getServiceNames() {
        StringBuilder sb = new StringBuilder();
        for (ServiceItem s : services) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s.getName());
        }
        return sb.toString();
    }
}
