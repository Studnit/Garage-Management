package com.citycarservices.garage.service;

import com.citycarservices.garage.config.Fmt;
import com.citycarservices.garage.config.GarageProperties;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.repo.JobCardRepository;
import com.citycarservices.garage.repo.ReminderRepository;
import com.citycarservices.garage.repo.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tracks next service date / kilometres, insurance expiry, PUC expiry and skipped recommended repairs.
 * A daily job creates reminders (once each) and tries to deliver them by email and/or the configured
 * WhatsApp/SMS webhook. Undelivered reminders stay pending so an advisor can send them manually.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ReminderRepository reminders;
    private final VehicleRepository vehicles;
    private final JobCardRepository jobCards;
    private final NotificationService notifier;
    private final GarageProperties props;
    private final AuditService audit;
    private final Fmt fmt = new Fmt();

    public ReminderService(ReminderRepository reminders, VehicleRepository vehicles, JobCardRepository jobCards,
                           NotificationService notifier, GarageProperties props, AuditService audit) {
        this.reminders = reminders;
        this.vehicles = vehicles;
        this.jobCards = jobCards;
        this.notifier = notifier;
        this.props = props;
        this.audit = audit;
    }

    @Scheduled(cron = "${garage.reminder-cron:0 0 9 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void scheduledRun() {
        try {
            int[] r = scanAndSend();
            log.info("Reminder run finished: {} created, {} delivered", r[0], r[1]);
        } catch (Exception e) {
            log.error("Reminder run failed", e);
        }
    }

    @Transactional(readOnly = true)
    public List<Reminder> list(ReminderStatus status) {
        if (status == null) {
            return reminders.findAll(org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "id"));
        }
        return reminders.findByStatusOrderByDueDateAscIdAsc(status);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return reminders.countByStatus(ReminderStatus.PENDING);
    }

    @Transactional
    public int[] scanAndSend() {
        int created = scan();
        int delivered = sendPending();
        return new int[] {created, delivered};
    }

    @Transactional
    public int scan() {
        int created = 0;
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(props.getReminderLeadDays());
        for (Vehicle v : vehicles.findByActiveTrue()) {
            String who = v.getRegNo() + " (" + v.getDisplayName() + ")";
            if (v.getNextServiceDate() != null && !v.getNextServiceDate().isAfter(limit)) {
                created += add(v, ReminderType.NEXT_SERVICE_DATE, "V" + v.getId() + ":SD:" + v.getNextServiceDate(),
                        v.getNextServiceDate(), null,
                        "your " + who + " is due for service on " + fmt.date(v.getNextServiceDate())
                                + ". Book online or call us to schedule it.");
            }
            if (v.getNextServiceKm() != null && v.getOdometer() >= v.getNextServiceKm() - props.getReminderKmLead()) {
                created += add(v, ReminderType.NEXT_SERVICE_KM, "V" + v.getId() + ":SK:" + v.getNextServiceKm(),
                        null, v.getNextServiceKm(),
                        "your " + who + " has run " + v.getOdometer() + " km and its service is due at "
                                + v.getNextServiceKm() + " km. Book a service to keep it running well.");
            }
            if (v.getInsuranceExpiry() != null && !v.getInsuranceExpiry().isAfter(limit)) {
                boolean past = v.getInsuranceExpiry().isBefore(today);
                created += add(v, ReminderType.INSURANCE_EXPIRY, "V" + v.getId() + ":INS:" + v.getInsuranceExpiry(),
                        v.getInsuranceExpiry(), null,
                        "the insurance of your " + who + (past ? " expired on " : " expires on ")
                                + fmt.date(v.getInsuranceExpiry()) + ". Please renew it on time.");
            }
            if (v.getPucExpiry() != null && !v.getPucExpiry().isAfter(limit)) {
                boolean past = v.getPucExpiry().isBefore(today);
                created += add(v, ReminderType.PUC_EXPIRY, "V" + v.getId() + ":PUC:" + v.getPucExpiry(),
                        v.getPucExpiry(), null,
                        "the PUC certificate of your " + who + (past ? " expired on " : " expires on ")
                                + fmt.date(v.getPucExpiry()) + ". We can get the emission test done at our centre.");
            }
        }
        // Recommended repairs the customer declined 30-180 days ago
        LocalDateTime newest = LocalDateTime.now().minusDays(30);
        LocalDateTime oldest = LocalDateTime.now().minusDays(180);
        for (JobCard j : jobCards.findByBookingStatusInOrderByCreatedAtDesc(
                List.of(BookingStatus.DELIVERED, BookingStatus.COMPLETED))) {
            if (j.getCompletedAt() == null || j.getCompletedAt().isAfter(newest) || j.getCompletedAt().isBefore(oldest)) {
                continue;
            }
            for (JobLine l : j.getLines()) {
                if (l.getApproval() == ApprovalStatus.REJECTED) {
                    created += add(j.getVehicle(), ReminderType.MAINTENANCE, "J" + j.getId() + ":L" + l.getId(),
                            LocalDate.now(), null,
                            "during the last visit of " + j.getVehicle().getRegNo() + " we recommended: "
                                    + l.getDescription() + ". It is still pending - please book a visit so it can be attended to.");
                }
            }
        }
        return created;
    }

    private int add(Vehicle v, ReminderType type, String key, LocalDate due, Integer dueKm, String body) {
        if (reminders.existsByDedupeKey(key)) {
            return 0;
        }
        Reminder r = new Reminder();
        r.setVehicle(v);
        r.setCustomer(v.getCustomer());
        r.setType(type);
        r.setDedupeKey(key);
        r.setDueDate(due);
        r.setDueKm(dueKm);
        r.setMessage("Hello " + v.getCustomer().getName() + ", " + body + " - " + props.getName() + ", "
                + props.getPhone());
        r.setStatus(ReminderStatus.PENDING);
        reminders.save(r);
        return 1;
    }

    @Transactional
    public int sendPending() {
        int delivered = 0;
        for (Reminder r : reminders.findByStatusOrderByDueDateAscIdAsc(ReminderStatus.PENDING)) {
            if (deliver(r)) {
                delivered++;
            }
        }
        return delivered;
    }

    @Transactional
    public boolean sendOne(Long id) {
        Reminder r = reminders.findById(id).orElseThrow(() -> new NotFoundException("Reminder not found"));
        return deliver(r);
    }

    private boolean deliver(Reminder r) {
        Customer c = r.getCustomer();
        NotificationService.Result res = notifier.send(c.getMobile(), c.getEmail(),
                r.getType().getLabel() + " - " + props.getName(), r.getMessage());
        r.setResult(res.detail());
        if (res.delivered()) {
            r.setStatus(ReminderStatus.SENT);
            r.setSentAt(LocalDateTime.now());
            r.setChannelsUsed(res.detail());
            return true;
        }
        if (res.attempted()) {
            r.setStatus(ReminderStatus.FAILED);
        }
        return false;
    }

    @Transactional
    public void markSentManually(Long id) {
        Reminder r = reminders.findById(id).orElseThrow(() -> new NotFoundException("Reminder not found"));
        r.setStatus(ReminderStatus.SENT);
        r.setSentAt(LocalDateTime.now());
        r.setChannelsUsed("Manual (WhatsApp/phone)");
        audit.log("REMINDER_MANUAL", r.getDedupeKey());
    }

    @Transactional
    public void dismiss(Long id) {
        Reminder r = reminders.findById(id).orElseThrow(() -> new NotFoundException("Reminder not found"));
        r.setStatus(ReminderStatus.DISMISSED);
    }
}
