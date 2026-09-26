package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.Fmt;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.repo.ApprovalLogRepository;
import com.citycarservices.garage.repo.EstimateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EstimateService {

    private final EstimateRepository estimates;
    private final ApprovalLogRepository approvalLogs;
    private final JobService jobService;
    private final InventoryService inventory;
    private final NumberService numbers;
    private final NotificationService notifier;
    private final AuditService audit;
    private final Fmt fmt = new Fmt();

    public EstimateService(EstimateRepository estimates, ApprovalLogRepository approvalLogs, JobService jobService,
                           InventoryService inventory, NumberService numbers, NotificationService notifier,
                           AuditService audit) {
        this.estimates = estimates;
        this.approvalLogs = approvalLogs;
        this.jobService = jobService;
        this.inventory = inventory;
        this.numbers = numbers;
        this.notifier = notifier;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Estimate get(Long id) {
        return estimates.findById(id).orElseThrow(() -> new NotFoundException("Estimate not found"));
    }

    @Transactional(readOnly = true)
    public Estimate getOwned(Long id, Long customerId) {
        Estimate e = get(id);
        if (!e.getJobCard().getCustomer().getId().equals(customerId)) {
            throw new NotFoundException("Estimate not found");
        }
        return e;
    }

    @Transactional(readOnly = true)
    public List<Estimate> forJob(Long jobId) {
        return estimates.findByJobCardIdOrderByIdDesc(jobId);
    }

    @Transactional(readOnly = true)
    public List<Estimate> forCustomer(Long customerId) {
        return estimates.findByJobCardCustomerIdOrderByIdDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalLog> history(Long jobId) {
        return approvalLogs.findByJobCardIdOrderByDecidedAtDescIdDesc(jobId);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return estimates.countByStatus(EstimateStatus.SENT);
    }

    /** Groups all draft items of the job into a quotation and sends it to the customer for approval. */
    @Transactional
    public Estimate generateAndSend(Long jobId, String note) {
        JobCard job = jobService.get(jobId);
        BookingStatus st = job.getBooking().getStatus();
        if (st.getStep() < BookingStatus.VEHICLE_RECEIVED.getStep() || st.getStep() >= BookingStatus.READY.getStep()) {
            throw new BusinessException("Estimates can be sent only while the vehicle is at the garage.");
        }
        List<JobLine> drafts = job.getLines().stream().filter(l -> l.getApproval() == ApprovalStatus.DRAFT).toList();
        if (drafts.isEmpty()) {
            throw new BusinessException("Add at least one labour, part or service item before sending an estimate.");
        }
        Estimate e = new Estimate();
        e.setEstimateNo(numbers.next("EST"));
        e.setJobCard(job);
        e.setStatus(EstimateStatus.SENT);
        e.setSentAt(LocalDateTime.now());
        e.setValidUntil(LocalDate.now().plusDays(7));
        e.setNote(Validators.blankToNull(note));
        e.setCreatedBy(CurrentUser.name());
        e = estimates.save(e);
        for (JobLine l : drafts) {
            l.setEstimate(e);
            l.setApproval(ApprovalStatus.AWAITING);
            e.getLines().add(l);
        }
        job.getEstimates().add(e);
        audit.log("ESTIMATE_SENT", e.getEstimateNo() + " for " + job.getJobNo() + " " + fmt.money(e.getGrandTotal()));
        Customer c = job.getCustomer();
        notifier.notifyAsync(c.getMobile(), c.getEmail(), "Estimate " + e.getEstimateNo() + " - City Car Services",
                "Hello " + c.getName() + ", the estimate " + e.getEstimateNo() + " of "
                        + fmt.money(e.getGrandTotal()) + " (incl. GST) for your " + job.getVehicle().getRegNo()
                        + " is ready. Please log in to the customer portal to approve or reject each repair, "
                        + "or call us.");
        return e;
    }

    /**
     * Records approve/reject decisions for individual lines.
     *
     * @param customerId when not null the estimate must belong to that customer (portal use)
     */
    @Transactional
    public Estimate decide(Long estimateId, Map<Long, ApprovalStatus> decisions, ApprovalChannel channel,
                           String decidedBy, String note, Long customerId) {
        Estimate e = customerId == null ? get(estimateId) : getOwned(estimateId, customerId);
        JobCard job = e.getJobCard();
        BookingStatus st = job.getBooking().getStatus();
        if (st.getStep() >= BookingStatus.READY.getStep() || st == BookingStatus.CANCELLED) {
            throw new BusinessException("This job is already closed, so the estimate can no longer be changed.");
        }
        int changed = 0;
        for (JobLine l : e.getLines()) {
            ApprovalStatus d = decisions.get(l.getId());
            if (l.getApproval() != ApprovalStatus.AWAITING || d == null
                    || (d != ApprovalStatus.APPROVED && d != ApprovalStatus.REJECTED)) {
                continue;
            }
            l.setApproval(d);
            l.setDecidedAt(LocalDateTime.now());
            ApprovalLog log = new ApprovalLog();
            log.setJobCard(job);
            log.setEstimate(e);
            log.setLineDescription(l.getDescription());
            log.setLineTotal(l.getTotal());
            log.setDecision(d);
            log.setChannel(channel);
            log.setDecidedBy(decidedBy);
            log.setNote(Validators.blankToNull(note));
            log.setDecidedAt(LocalDateTime.now());
            approvalLogs.save(log);
            changed++;
        }
        if (changed == 0) {
            throw new BusinessException("Choose approve or reject for at least one pending item.");
        }
        refreshStatus(e);
        if (st.getStep() >= BookingStatus.IN_SERVICE.getStep()) {
            inventory.issueForJob(job, false);
        }
        audit.log("ESTIMATE_DECIDED", e.getEstimateNo() + " " + changed + " item(s) via " + channel);
        return e;
    }

    public Map<Long, ApprovalStatus> allAwaiting(Estimate e, ApprovalStatus decision) {
        Map<Long, ApprovalStatus> m = new HashMap<>();
        for (JobLine l : e.getLines()) {
            if (l.getApproval() == ApprovalStatus.AWAITING) {
                m.put(l.getId(), decision);
            }
        }
        return m;
    }

    private void refreshStatus(Estimate e) {
        long awaiting = e.getLines().stream().filter(l -> l.getApproval() == ApprovalStatus.AWAITING).count();
        long approved = e.getLines().stream().filter(l -> l.getApproval() == ApprovalStatus.APPROVED).count();
        long total = e.getLines().size();
        if (awaiting > 0) {
            e.setStatus(EstimateStatus.SENT);
            return;
        }
        e.setRespondedAt(LocalDateTime.now());
        if (approved == total) {
            e.setStatus(EstimateStatus.APPROVED);
        } else if (approved == 0) {
            e.setStatus(EstimateStatus.REJECTED);
        } else {
            e.setStatus(EstimateStatus.PARTIAL);
        }
    }
}
