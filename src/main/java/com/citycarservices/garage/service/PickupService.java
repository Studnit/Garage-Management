package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.DriverForm;
import com.citycarservices.garage.repo.DriverRepository;
import com.citycarservices.garage.repo.PickupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class PickupService {

    private final PickupRepository pickups;
    private final DriverRepository drivers;
    private final AuditService audit;

    public PickupService(PickupRepository pickups, DriverRepository drivers, AuditService audit) {
        this.pickups = pickups;
        this.drivers = drivers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Pickup get(Long id) {
        return pickups.findById(id).orElseThrow(() -> new NotFoundException("Pickup request not found"));
    }

    @Transactional(readOnly = true)
    public List<Pickup> open() {
        return pickups.findByStatusInOrderByScheduledAtAscIdAsc(
                EnumSet.of(PickupStatus.REQUESTED, PickupStatus.ASSIGNED, PickupStatus.EN_ROUTE));
    }

    @Transactional(readOnly = true)
    public List<Pickup> recentClosed() {
        return pickups.findByStatusInOrderByScheduledAtAscIdAsc(EnumSet.of(PickupStatus.DONE, PickupStatus.CANCELLED))
                .stream().sorted((a, b) -> b.getId().compareTo(a.getId())).limit(50).toList();
    }

    @Transactional(readOnly = true)
    public List<Pickup> forBooking(Long bookingId) {
        return pickups.findByBookingIdOrderByIdAsc(bookingId);
    }

    @Transactional(readOnly = true)
    public List<Pickup> forCustomer(Long customerId) {
        return pickups.findByBookingCustomerIdOrderByIdDesc(customerId);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return open().size();
    }

    @Transactional
    public Pickup createRequest(Booking b, PickupType type, String address, LocalDateTime when) {
        Pickup p = new Pickup();
        p.setBooking(b);
        p.setType(type);
        p.setStatus(PickupStatus.REQUESTED);
        p.setAddress(address);
        p.setContactPhone(b.getCustomer().getMobile());
        p.setScheduledAt(when);
        return pickups.save(p);
    }

    @Transactional
    public void assign(Long pickupId, Long driverId, LocalDateTime scheduledAt, String notes, String locationLink) {
        Pickup p = get(pickupId);
        if (p.getStatus() == PickupStatus.DONE || p.getStatus() == PickupStatus.CANCELLED) {
            throw new BusinessException("This request is already closed.");
        }
        Driver d = drivers.findById(driverId).orElseThrow(() -> new NotFoundException("Driver not found"));
        if (!d.isActive()) {
            throw new BusinessException("This driver is not active.");
        }
        p.setDriver(d);
        if (scheduledAt != null) {
            p.setScheduledAt(scheduledAt);
        }
        p.setNotes(Validators.blankToNull(notes));
        p.setLocationLink(validLink(locationLink));
        if (p.getStatus() == PickupStatus.REQUESTED) {
            p.setStatus(PickupStatus.ASSIGNED);
        }
        audit.log("PICKUP_ASSIGNED", p.getBooking().getBookingNo() + " -> " + d.getName());
    }

    private String validLink(String link) {
        String l = Validators.blankToNull(link);
        if (l != null && !(l.startsWith("https://") || l.startsWith("http://"))) {
            throw new BusinessException("Location link must start with http:// or https://");
        }
        return l;
    }

    /** ASSIGNED -> EN_ROUTE -> DONE. */
    @Transactional
    public void advance(Long pickupId, String confirmation) {
        Pickup p = get(pickupId);
        switch (p.getStatus()) {
            case REQUESTED -> throw new BusinessException("Assign a driver first.");
            case ASSIGNED -> p.setStatus(PickupStatus.EN_ROUTE);
            case EN_ROUTE -> {
                if (p.getType() == PickupType.DROP && (confirmation == null || confirmation.isBlank())) {
                    throw new BusinessException("Enter who received the vehicle to confirm delivery.");
                }
                p.setStatus(PickupStatus.DONE);
                p.setCompletedAt(LocalDateTime.now());
                p.setConfirmation(Validators.blankToNull(confirmation));
            }
            default -> throw new BusinessException("This request is already closed.");
        }
        audit.log("PICKUP_STATUS", p.getBooking().getBookingNo() + " " + p.getType() + " " + p.getStatus());
    }

    @Transactional
    public void cancel(Long pickupId) {
        Pickup p = get(pickupId);
        if (p.getStatus() == PickupStatus.DONE || p.getStatus() == PickupStatus.CANCELLED) {
            throw new BusinessException("This request is already closed.");
        }
        p.setStatus(PickupStatus.CANCELLED);
    }

    @Transactional
    public void cancelOpenForBooking(Long bookingId) {
        for (Pickup p : pickups.findByBookingIdOrderByIdAsc(bookingId)) {
            if (p.getStatus() != PickupStatus.DONE && p.getStatus() != PickupStatus.CANCELLED) {
                p.setStatus(PickupStatus.CANCELLED);
            }
        }
    }

    /** Called when the vehicle is received at the garage: pickups for the booking are considered done. */
    @Transactional
    public void completeOpen(Long bookingId, PickupType type, String confirmation) {
        for (Pickup p : pickups.findByBookingIdOrderByIdAsc(bookingId)) {
            if (p.getType() == type && p.getStatus() != PickupStatus.DONE && p.getStatus() != PickupStatus.CANCELLED) {
                p.setStatus(PickupStatus.DONE);
                p.setCompletedAt(LocalDateTime.now());
                if (p.getConfirmation() == null) {
                    p.setConfirmation(confirmation);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean hasType(Long bookingId, PickupType type) {
        return pickups.findByBookingIdOrderByIdAsc(bookingId).stream()
                .anyMatch(p -> p.getType() == type && p.getStatus() != PickupStatus.CANCELLED);
    }

    // ---------- drivers ----------

    @Transactional(readOnly = true)
    public List<Driver> drivers() {
        return drivers.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Driver> activeDrivers() {
        return drivers.findByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public Driver saveDriver(DriverForm f) {
        Driver d = new Driver();
        d.setName(f.getName().trim());
        d.setPhone(Validators.mobile(f.getPhone()));
        d.setVehicleNo(f.getVehicleNo() == null ? null : f.getVehicleNo().trim().toUpperCase());
        return drivers.save(d);
    }

    @Transactional
    public void toggleDriver(Long id) {
        Driver d = drivers.findById(id).orElseThrow(() -> new NotFoundException("Driver not found"));
        d.setActive(!d.isActive());
    }
}
