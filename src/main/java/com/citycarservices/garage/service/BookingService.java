package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.Fmt;
import com.citycarservices.garage.config.GarageProperties;
import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.BookingForm;
import com.citycarservices.garage.repo.BookingRepository;
import com.citycarservices.garage.repo.ServiceItemRepository;
import com.citycarservices.garage.repo.VehicleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Owns the booking lifecycle:
 * Pending -> Confirmed -> Vehicle Received -> Inspection -> In Service -> Quality Check -> Ready -> Delivered -> Completed.
 * Each step has guards so that the data chain Booking -> Job card -> Inspection -> Estimate/Approval -> Parts/Labour
 * -> Invoice -> Payment -> Delivery -> Service history stays consistent.
 */
@Service
public class BookingService {

    private static final Set<Role> ADVISORS = EnumSet.of(Role.SUPER_ADMIN, Role.GARAGE_MANAGER, Role.SERVICE_ADVISOR);
    private static final List<BookingStatus> OPEN = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED,
            BookingStatus.VEHICLE_RECEIVED, BookingStatus.INSPECTION, BookingStatus.IN_SERVICE,
            BookingStatus.QUALITY_CHECK, BookingStatus.READY, BookingStatus.DELIVERED);

    private final BookingRepository bookings;
    private final ServiceItemRepository serviceItems;
    private final VehicleRepository vehicles;
    private final PickupService pickups;
    private final JobService jobs;
    private final InspectionService inspections;
    private final InventoryService inventory;
    private final InvoiceService invoices;
    private final NumberService numbers;
    private final NotificationService notifier;
    private final AuditService audit;
    private final GarageProperties props;
    private final Fmt fmt = new Fmt();

    public BookingService(BookingRepository bookings, ServiceItemRepository serviceItems, VehicleRepository vehicles,
                          PickupService pickups, JobService jobs, InspectionService inspections,
                          InventoryService inventory, InvoiceService invoices, NumberService numbers,
                          NotificationService notifier, AuditService audit, GarageProperties props) {
        this.bookings = bookings;
        this.serviceItems = serviceItems;
        this.vehicles = vehicles;
        this.pickups = pickups;
        this.jobs = jobs;
        this.inspections = inspections;
        this.inventory = inventory;
        this.invoices = invoices;
        this.numbers = numbers;
        this.notifier = notifier;
        this.audit = audit;
        this.props = props;
    }

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Booking get(Long id) {
        return bookings.findById(id).orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public Booking getOwned(Long id, Long customerId) {
        Booking b = get(id);
        if (!b.getCustomer().getId().equals(customerId)) {
            throw new NotFoundException("Booking not found");
        }
        return b;
    }

    @Transactional(readOnly = true)
    public List<Booking> forCustomer(Long customerId) {
        return bookings.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Booking> forVehicle(Long vehicleId) {
        return bookings.findByVehicleIdOrderByCreatedAtDesc(vehicleId);
    }

    @Transactional(readOnly = true)
    public List<Booking> search(String q, BookingStatus status, LocalDate from, LocalDate to) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return bookings.findAll(PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "id"))).getContent().stream()
                .filter(b -> status == null || b.getStatus() == status)
                .filter(b -> from == null || !b.getPreferredAt().toLocalDate().isBefore(from))
                .filter(b -> to == null || !b.getPreferredAt().toLocalDate().isAfter(to))
                .filter(b -> needle.isEmpty()
                        || b.getBookingNo().toLowerCase(Locale.ROOT).contains(needle)
                        || b.getVehicle().getRegNo().toLowerCase(Locale.ROOT).contains(needle)
                        || b.getCustomer().getName().toLowerCase(Locale.ROOT).contains(needle)
                        || b.getCustomer().getMobile().contains(needle))
                .toList();
    }

    // ---------- creation ----------

    @Transactional
    public Booking create(BookingForm f, Customer customer, BookingSource source, boolean confirmed) {
        Vehicle v = vehicles.findById(f.getVehicleId()).orElseThrow(() -> new NotFoundException("Vehicle not found"));
        if (!v.getCustomer().getId().equals(customer.getId())) {
            throw new NotFoundException("Vehicle not found");
        }
        LocalDateTime t = f.getPreferredAt();
        LocalDateTime now = LocalDateTime.now();
        if (t.isBefore(source == BookingSource.ONLINE ? now : now.minusHours(4))) {
            throw new BusinessException("Please choose a date and time in the future.");
        }
        if (t.isAfter(now.plusDays(90))) {
            throw new BusinessException("Bookings can be made up to 90 days ahead.");
        }
        if (t.getHour() < props.getOpenHour() || t.getHour() >= props.getCloseHour()) {
            throw new BusinessException("We are open " + props.getHoursText() + ". Please choose a time within working hours.");
        }
        List<ServiceItem> selected = f.getServiceIds() == null || f.getServiceIds().isEmpty()
                ? List.of() : serviceItems.findAllById(f.getServiceIds()).stream().filter(ServiceItem::isActive).toList();
        if (selected.isEmpty() && (f.getComplaint() == null || f.getComplaint().isBlank())) {
            throw new BusinessException("Select at least one service or describe the problem.");
        }
        if (bookings.countByVehicleIdAndStatusIn(v.getId(), OPEN) > 0) {
            throw new BusinessException("Vehicle " + v.getRegNo() + " already has an active booking. "
                    + "Please complete or cancel it before creating a new one.");
        }
        String pickupAddress = f.getPickupAddress();
        if (f.isPickupRequired()) {
            if (pickupAddress == null || pickupAddress.isBlank()) {
                pickupAddress = customer.getAddress();
            }
            if (pickupAddress == null || pickupAddress.isBlank()) {
                throw new BusinessException("Enter the pickup address.");
            }
        }
        Booking b = new Booking();
        b.setBookingNo(numbers.next("BK"));
        b.setCustomer(customer);
        b.setVehicle(v);
        b.getServices().addAll(selected);
        b.setPreferredAt(t);
        b.setPickupRequired(f.isPickupRequired());
        b.setPickupAddress(f.isPickupRequired() ? pickupAddress.trim() : null);
        b.setComplaint(Validators.blankToNull(f.getComplaint()));
        b.setSource(source);
        b.setStatus(confirmed ? BookingStatus.CONFIRMED : BookingStatus.PENDING);
        b = bookings.save(b);
        log(b, b.getStatus(), "Booking created (" + source.getLabel() + ")");
        if (f.isPickupRequired()) {
            pickups.createRequest(b, PickupType.PICKUP, b.getPickupAddress(), t);
        }
        audit.log("BOOKING_CREATED", b.getBookingNo() + " " + v.getRegNo());
        notifier.notifyAsync(customer.getMobile(), customer.getEmail(), "Booking " + b.getBookingNo() + " - City Car Services",
                "Hello " + customer.getName() + ", your service booking " + b.getBookingNo() + " for " + v.getRegNo()
                        + " on " + fmt.dateTime(t) + " is " + (confirmed ? "confirmed" : "received and will be confirmed shortly")
                        + ". " + props.getName() + ", " + props.getPhone() + ".");
        return b;
    }

    // ---------- lifecycle ----------

    /** Moves to the next status in the flow (except Vehicle Received, which needs extra data). */
    @Transactional
    public Booking advance(Long id, String note) {
        Booking b = get(id);
        BookingStatus from = b.getStatus();
        BookingStatus to = from.next();
        if (to == null) {
            throw new BusinessException("This booking is already " + from.getLabel().toLowerCase(Locale.ROOT) + ".");
        }
        if (to == BookingStatus.VEHICLE_RECEIVED) {
            throw new BusinessException("Use 'Receive vehicle' to record the odometer reading and open the job card.");
        }
        assertRole(to);
        JobCard job = b.getJobCard();
        switch (to) {
            case CONFIRMED -> {
                // nothing extra
            }
            case INSPECTION -> {
                jobs.assertCanWork(job);
                if (job.getMechanic() == null) {
                    throw new BusinessException("Assign a mechanic to the job card first.");
                }
                inspections.getOrCreate(job);
            }
            case IN_SERVICE -> {
                Inspection i = inspections.find(job.getId());
                if (i == null || !i.isSubmitted()) {
                    throw new BusinessException("The mechanic must submit the inspection report first.");
                }
                long awaiting = job.countLines(ApprovalStatus.AWAITING);
                if (awaiting > 0) {
                    throw new BusinessException("Waiting for customer approval on " + awaiting + " item(s). "
                            + "Record the customer's decision on the Estimate tab.");
                }
                if (job.countLines(ApprovalStatus.APPROVED) == 0) {
                    throw new BusinessException("There is no approved work. Send an estimate and get the customer's approval first.");
                }
                inventory.issueForJob(job, true);
                job.setStatus(JobStatus.IN_PROGRESS);
            }
            case QUALITY_CHECK -> {
                jobs.assertCanWork(job);
                long awaiting = job.countLines(ApprovalStatus.AWAITING);
                if (awaiting > 0) {
                    throw new BusinessException(awaiting + " additional item(s) still await customer approval.");
                }
                inventory.issueForJob(job, true);
                job.setQcAt(null);
                job.setQcBy(null);
            }
            case READY -> {
                if (job.getQcAt() == null) {
                    throw new BusinessException("Record the quality check result (pass) first.");
                }
                if (job.countLines(ApprovalStatus.AWAITING) > 0) {
                    throw new BusinessException("Some estimate items still await customer approval.");
                }
                invoices.generateForJob(job);
                jobs.markCompleted(job);
                if (b.isPickupRequired() && !pickups.hasType(b.getId(), PickupType.DROP)) {
                    String addr = b.getPickupAddress() != null ? b.getPickupAddress() : b.getCustomer().getAddress();
                    pickups.createRequest(b, PickupType.DROP, addr, LocalDateTime.now().plusHours(2));
                }
            }
            case DELIVERED -> {
                Invoice inv = invoices.forJob(job.getId());
                if (inv == null) {
                    throw new BusinessException("Generate the invoice before delivery.");
                }
                if (inv.getBalance().signum() > 0) {
                    GarageUserDetails me = CurrentUser.get();
                    if (me == null || !me.isManagement()) {
                        throw new BusinessException("Collect the balance of " + fmt.money(inv.getBalance())
                                + " before delivery. Only a manager can approve delivery on credit.");
                    }
                    audit.log("CREDIT_DELIVERY", inv.getInvoiceNo() + " balance " + fmt.money(inv.getBalance()));
                }
                pickups.completeOpen(b.getId(), PickupType.DROP, "Delivered by garage");
            }
            case COMPLETED -> {
                Invoice inv = invoices.forJob(job.getId());
                if (inv == null || !inv.isPaid()) {
                    throw new BusinessException("The invoice must be fully paid before the job can be completed.");
                }
                finishVehicleRecord(b, job);
            }
            default -> throw new BusinessException("Invalid status change.");
        }
        setStatus(b, to, note);
        notifyStatus(b, to);
        return b;
    }

    @Transactional
    public Booking receiveVehicle(Long id, Integer odometer, String fuelLevel, String notes) {
        Booking b = get(id);
        assertRole(BookingStatus.VEHICLE_RECEIVED);
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Only confirmed bookings can be received. Confirm the booking first.");
        }
        if (odometer == null || odometer < 0) {
            throw new BusinessException("Enter the odometer reading.");
        }
        Vehicle v = b.getVehicle();
        if (odometer < v.getOdometer()) {
            throw new BusinessException("Odometer reading is lower than the last recorded reading ("
                    + v.getOdometer() + " km). Please re-check.");
        }
        JobCard job = jobs.open(b, odometer, fuelLevel, notes);
        b.setJobCard(job);
        v.setOdometer(odometer);
        pickups.completeOpen(b.getId(), PickupType.PICKUP, "Received at garage");
        setStatus(b, BookingStatus.VEHICLE_RECEIVED, "Received at " + odometer + " km");
        notifyStatus(b, BookingStatus.VEHICLE_RECEIVED);
        return b;
    }

    /** Quality check outcome. A failed check sends the job back to In Service for rework. */
    @Transactional
    public void recordQc(Long id, boolean passed, String notes) {
        Booking b = get(id);
        assertRole(BookingStatus.READY);
        if (b.getStatus() != BookingStatus.QUALITY_CHECK) {
            throw new BusinessException("The job is not in Quality Check.");
        }
        JobCard job = b.getJobCard();
        if (passed) {
            job.setQcAt(LocalDateTime.now());
            job.setQcBy(CurrentUser.name());
            job.setQcNotes(Validators.blankToNull(notes));
            audit.log("QC_PASSED", job.getJobNo());
        } else {
            if (notes == null || notes.isBlank()) {
                throw new BusinessException("Describe what needs rework.");
            }
            job.setQcAt(null);
            job.setQcNotes(notes.trim());
            job.setStatus(JobStatus.IN_PROGRESS);
            setStatus(b, BookingStatus.IN_SERVICE, "QC failed - rework: " + notes.trim());
            audit.log("QC_FAILED", job.getJobNo());
        }
    }

    @Transactional
    public void cancel(Long id, String reason, Long customerRestriction) {
        Booking b = customerRestriction == null ? get(id) : getOwned(id, customerRestriction);
        if (b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Only bookings that have not reached the garage can be cancelled. "
                    + "Please call " + props.getPhone() + ".");
        }
        if (customerRestriction == null) {
            assertRole(BookingStatus.CONFIRMED);
        }
        b.setCancelReason(Validators.blankToNull(reason));
        pickups.cancelOpenForBooking(b.getId());
        setStatus(b, BookingStatus.CANCELLED, reason == null || reason.isBlank() ? "Cancelled" : "Cancelled: " + reason.trim());
    }

    @Transactional
    public void reschedule(Long id, LocalDateTime when, Long customerRestriction) {
        Booking b = customerRestriction == null ? get(id) : getOwned(id, customerRestriction);
        if (b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Only pending or confirmed bookings can be rescheduled.");
        }
        if (when == null || when.isBefore(LocalDateTime.now())) {
            throw new BusinessException("Please choose a date and time in the future.");
        }
        if (when.getHour() < props.getOpenHour() || when.getHour() >= props.getCloseHour()) {
            throw new BusinessException("We are open " + props.getHoursText() + ".");
        }
        b.setPreferredAt(when);
        for (Pickup p : pickups.forBooking(b.getId())) {
            if (p.getType() == PickupType.PICKUP && p.getStatus() != PickupStatus.DONE && p.getStatus() != PickupStatus.CANCELLED) {
                p.setScheduledAt(when);
            }
        }
        log(b, b.getStatus(), "Rescheduled to " + fmt.dateTime(when));
        audit.log("BOOKING_RESCHEDULED", b.getBookingNo() + " " + fmt.dateTime(when));
    }

    // ---------- helpers ----------

    private void assertRole(BookingStatus to) {
        Role r = CurrentUser.role();
        if (r == null) {
            throw new BusinessException("You are not allowed to do this.");
        }
        boolean ok = switch (to) {
            case CONFIRMED, VEHICLE_RECEIVED, IN_SERVICE, READY, DELIVERED -> ADVISORS.contains(r);
            case INSPECTION, QUALITY_CHECK -> ADVISORS.contains(r) || r == Role.MECHANIC;
            case COMPLETED -> ADVISORS.contains(r) || r == Role.ACCOUNTANT;
            default -> false;
        };
        if (!ok) {
            throw new BusinessException("Your role (" + r.getLabel() + ") cannot move a job to \"" + to.getLabel() + "\".");
        }
    }

    private void setStatus(Booking b, BookingStatus to, String note) {
        b.setStatus(to);
        log(b, to, note);
        audit.log("BOOKING_STATUS", b.getBookingNo() + " -> " + to);
    }

    private void log(Booking b, BookingStatus s, String note) {
        BookingStatusLog l = new BookingStatusLog();
        l.setBooking(b);
        l.setStatus(s);
        l.setChangedAt(LocalDateTime.now());
        l.setChangedBy(CurrentUser.name());
        l.setNote(Validators.blankToNull(note));
        b.getLogs().add(l);
    }

    private void finishVehicleRecord(Booking b, JobCard job) {
        Vehicle v = b.getVehicle();
        v.setLastServiceDate(LocalDate.now());
        int odo = job.getOdometerOut() != null ? job.getOdometerOut() : job.getOdometerIn();
        if (odo > v.getOdometer()) {
            v.setOdometer(odo);
        }
        Integer km = null;
        Integer months = null;
        for (ServiceItem s : b.getServices()) {
            if (s.getIntervalKm() != null && s.getIntervalKm() > 0) {
                km = km == null ? s.getIntervalKm() : Math.min(km, s.getIntervalKm());
            }
            if (s.getIntervalMonths() != null && s.getIntervalMonths() > 0) {
                months = months == null ? s.getIntervalMonths() : Math.min(months, s.getIntervalMonths());
            }
        }
        if (km != null) {
            v.setNextServiceKm(v.getOdometer() + km);
        }
        if (months != null) {
            v.setNextServiceDate(LocalDate.now().plusMonths(months));
        }
    }

    private void notifyStatus(Booking b, BookingStatus to) {
        String text;
        switch (to) {
            case CONFIRMED -> text = "your booking " + b.getBookingNo() + " is confirmed for " + fmt.dateTime(b.getPreferredAt());
            case VEHICLE_RECEIVED -> text = "we have received your " + b.getVehicle().getRegNo() + ". Our team will inspect it shortly";
            case READY -> text = "your " + b.getVehicle().getRegNo() + " is ready for delivery";
            case DELIVERED -> text = "your " + b.getVehicle().getRegNo() + " has been delivered. Thank you for choosing us";
            case COMPLETED -> text = "your job " + b.getBookingNo() + " is completed. We would love your feedback in the customer portal";
            default -> {
                return;
            }
        }
        Customer c = b.getCustomer();
        notifier.notifyAsync(c.getMobile(), c.getEmail(), "Update on " + b.getBookingNo() + " - City Car Services",
                "Hello " + c.getName() + ", " + text + ". " + props.getName() + ", " + props.getPhone() + ".");
    }
}
