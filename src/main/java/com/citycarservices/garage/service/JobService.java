package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.JobLineForm;
import com.citycarservices.garage.repo.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class JobService {

    private final JobCardRepository jobs;
    private final PartRepository parts;
    private final MechanicRepository mechanics;
    private final NumberService numbers;
    private final AuditService audit;

    public JobService(JobCardRepository jobs, PartRepository parts, MechanicRepository mechanics,
                      NumberService numbers, AuditService audit) {
        this.jobs = jobs;
        this.parts = parts;
        this.mechanics = mechanics;
        this.numbers = numbers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public JobCard get(Long id) {
        return jobs.findById(id).orElseThrow(() -> new NotFoundException("Job card not found"));
    }

    @Transactional(readOnly = true)
    public JobCard getOwned(Long id, Long customerId) {
        JobCard j = get(id);
        if (!j.getCustomer().getId().equals(customerId)) {
            throw new NotFoundException("Job card not found");
        }
        return j;
    }

    @Transactional(readOnly = true)
    public JobCard forBooking(Long bookingId) {
        return jobs.findByBookingId(bookingId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<JobCard> search(String q, BookingStatus status, Long mechanicId, boolean activeOnly) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return jobs.findAll(PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "id"))).getContent().stream()
                .filter(j -> status == null || j.getBooking().getStatus() == status)
                .filter(j -> mechanicId == null || (j.getMechanic() != null && j.getMechanic().getId().equals(mechanicId)))
                .filter(j -> !activeOnly || j.getBooking().getStatus().isInGarage())
                .filter(j -> needle.isEmpty()
                        || j.getJobNo().toLowerCase(Locale.ROOT).contains(needle)
                        || j.getVehicle().getRegNo().toLowerCase(Locale.ROOT).contains(needle)
                        || j.getCustomer().getName().toLowerCase(Locale.ROOT).contains(needle)
                        || j.getCustomer().getMobile().contains(needle))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobCard> forMechanic(Long mechanicId) {
        return jobs.findByMechanicIdOrderByCreatedAtDesc(mechanicId);
    }

    /** Opens the job card when the vehicle is received. Booked services become draft estimate lines. */
    @Transactional
    public JobCard open(Booking b, int odometer, String fuelLevel, String notes) {
        JobCard j = new JobCard();
        j.setJobNo(numbers.next("JC"));
        j.setBooking(b);
        j.setCustomer(b.getCustomer());
        j.setVehicle(b.getVehicle());
        j.setComplaint(b.getComplaint());
        j.setOdometerIn(odometer);
        j.setFuelLevel(Validators.blankToNull(fuelLevel));
        j.setAdvisorNotes(Validators.blankToNull(notes));
        j.setStatus(JobStatus.OPEN);
        j = jobs.save(j);
        for (ServiceItem s : b.getServices()) {
            JobLine l = new JobLine();
            l.setJobCard(j);
            l.setLineType(LineType.SERVICE);
            l.setDescription(s.getName());
            l.setQuantity(BigDecimal.ONE);
            l.setUnitPrice(s.getBasePrice());
            l.setGstRate(s.getGstRate());
            l.setHsnSac(s.getSacCode());
            l.setLabourHours(s.getEstimatedHours());
            l.setOrigin("INITIAL");
            l.setAddedBy("Booking");
            j.getLines().add(l);
        }
        audit.log("JOB_OPENED", j.getJobNo() + " " + b.getVehicle().getRegNo());
        return j;
    }

    /** Mechanics may only touch jobs assigned to them. */
    public void assertCanWork(JobCard job) {
        GarageUserDetails me = CurrentUser.get();
        if (me != null && me.getRole() == Role.MECHANIC) {
            if (job.getMechanic() == null || !job.getMechanic().getUser().getId().equals(me.getId())) {
                throw new BusinessException("This job is not assigned to you.");
            }
        }
    }

    @Transactional
    public void assignMechanic(Long jobId, Long mechanicId) {
        JobCard j = get(jobId);
        if (j.getBooking().getStatus().getStep() >= BookingStatus.READY.getStep()
                || j.getBooking().getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("The job is already closed.");
        }
        Mechanic m = mechanics.findById(mechanicId).orElseThrow(() -> new NotFoundException("Mechanic not found"));
        if (!m.isActive()) {
            throw new BusinessException("This mechanic is not active.");
        }
        j.setMechanic(m);
        if (j.getInspection() != null) {
            j.getInspection().setMechanic(m);
        }
        audit.log("MECHANIC_ASSIGNED", j.getJobNo() + " -> " + m.getName());
    }

    @Transactional
    public JobLine addLine(Long jobId, JobLineForm f) {
        JobCard j = get(jobId);
        assertCanWork(j);
        BookingStatus s = j.getBooking().getStatus();
        if (s.getStep() < BookingStatus.VEHICLE_RECEIVED.getStep() || s.getStep() >= BookingStatus.READY.getStep()) {
            throw new BusinessException("Items can be added only while the vehicle is at the garage and before it is marked Ready.");
        }
        JobLine l = new JobLine();
        l.setJobCard(j);
        l.setLineType(f.getLineType());
        l.setQuantity(f.getQuantity());
        if (f.getLineType() == LineType.PART) {
            Part p = f.getPartId() == null ? null : parts.findById(f.getPartId()).orElse(null);
            if (p == null && (f.getDescription() == null || f.getDescription().isBlank())) {
                throw new BusinessException("Select a part from the catalogue or type a description.");
            }
            l.setPart(p);
            l.setDescription(f.getDescription() != null && !f.getDescription().isBlank() ? f.getDescription().trim()
                    : p.getName() + (p.getBrand() == null ? "" : " (" + p.getBrand() + ")"));
            l.setUnitPrice(f.getUnitPrice() != null ? Money.round(f.getUnitPrice())
                    : (p == null ? null : p.getSellingPrice()));
            l.setGstRate(f.getGstRate() != null ? f.getGstRate() : (p == null ? new BigDecimal("28.00") : p.getGstRate()));
            l.setHsnSac(p == null ? null : p.getHsnCode());
        } else {
            if (f.getDescription() == null || f.getDescription().isBlank()) {
                throw new BusinessException("Enter a description for the item.");
            }
            l.setDescription(f.getDescription().trim());
            l.setUnitPrice(f.getUnitPrice() == null ? null : Money.round(f.getUnitPrice()));
            l.setGstRate(f.getGstRate() != null ? f.getGstRate() : new BigDecimal("18.00"));
            l.setHsnSac("9987");
            l.setLabourHours(f.getLineType() == LineType.LABOUR
                    ? (f.getLabourHours() != null ? f.getLabourHours() : f.getQuantity()) : f.getLabourHours());
        }
        if (l.getUnitPrice() == null) {
            throw new BusinessException("Enter the price.");
        }
        l.setOrigin(s.getStep() <= BookingStatus.INSPECTION.getStep() ? "INITIAL" : "ADDITIONAL");
        l.setAddedBy(CurrentUser.name());
        l.setApproval(ApprovalStatus.DRAFT);
        j.getLines().add(l);
        audit.log("JOB_LINE_ADDED", j.getJobNo() + " " + l.getDescription());
        return l;
    }

    @Transactional
    public void updateLine(Long jobId, Long lineId, BigDecimal qty, BigDecimal unitPrice) {
        JobCard j = get(jobId);
        assertCanWork(j);
        JobLine l = findLine(j, lineId);
        if (l.getApproval() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Only draft items can be edited. Sent items are locked for the customer.");
        }
        if (qty == null || qty.signum() <= 0 || unitPrice == null || unitPrice.signum() < 0) {
            throw new BusinessException("Enter a valid quantity and price.");
        }
        l.setQuantity(qty);
        l.setUnitPrice(Money.round(unitPrice));
    }

    @Transactional
    public void removeLine(Long jobId, Long lineId) {
        JobCard j = get(jobId);
        assertCanWork(j);
        JobLine l = findLine(j, lineId);
        if (l.getApproval() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Only draft items can be removed.");
        }
        j.getLines().remove(l);
        audit.log("JOB_LINE_REMOVED", j.getJobNo() + " " + l.getDescription());
    }

    private JobLine findLine(JobCard j, Long lineId) {
        return j.getLines().stream().filter(x -> x.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Item not found"));
    }

    @Transactional
    public void logHours(Long jobId, BigDecimal hours) {
        JobCard j = get(jobId);
        assertCanWork(j);
        if (hours == null || hours.signum() <= 0 || hours.compareTo(new BigDecimal("24")) > 0) {
            throw new BusinessException("Enter hours between 0.1 and 24.");
        }
        j.setActualHours(j.getActualHours().add(hours));
        audit.log("HOURS_LOGGED", j.getJobNo() + " +" + hours + "h");
    }

    @Transactional
    public void saveNotes(Long jobId, String notes, Integer odometerOut, String fuelLevel) {
        JobCard j = get(jobId);
        j.setAdvisorNotes(Validators.blankToNull(notes));
        j.setFuelLevel(Validators.blankToNull(fuelLevel));
        if (odometerOut != null) {
            if (odometerOut < j.getOdometerIn()) {
                throw new BusinessException("Odometer out cannot be lower than odometer in.");
            }
            j.setOdometerOut(odometerOut);
        }
    }

    @Transactional
    public void setHold(Long jobId, boolean hold) {
        JobCard j = get(jobId);
        if (j.getStatus() == JobStatus.COMPLETED) {
            throw new BusinessException("The job is already completed.");
        }
        j.setStatus(hold ? JobStatus.ON_HOLD : JobStatus.IN_PROGRESS);
        audit.log(hold ? "JOB_HOLD" : "JOB_RESUMED", j.getJobNo());
    }

    @Transactional
    public void markCompleted(JobCard j) {
        j.setStatus(JobStatus.COMPLETED);
        j.setCompletedAt(LocalDateTime.now());
    }
}
