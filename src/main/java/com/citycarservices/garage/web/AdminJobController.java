package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.config.Roles;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.InspectionForm;
import com.citycarservices.garage.form.JobLineForm;
import com.citycarservices.garage.form.PaymentForm;
import com.citycarservices.garage.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/jobs")
public class AdminJobController extends BaseController {

    private final JobService jobs;
    private final InspectionService inspections;
    private final EstimateService estimates;
    private final MechanicService mechanics;
    private final InventoryService inventory;
    private final InvoiceService invoices;
    private final PaymentService payments;

    public AdminJobController(JobService jobs, InspectionService inspections, EstimateService estimates,
                              MechanicService mechanics, InventoryService inventory, InvoiceService invoices,
                              PaymentService payments) {
        this.jobs = jobs;
        this.inspections = inspections;
        this.estimates = estimates;
        this.mechanics = mechanics;
        this.inventory = inventory;
        this.invoices = invoices;
        this.payments = payments;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "false") boolean active,
                       @RequestParam(defaultValue = "false") boolean mine,
                       @AuthenticationPrincipal GarageUserDetails me, Model model) {
        BookingStatus st = parseEnum(BookingStatus.class, status);
        Long mechanicId = null;
        boolean onlyMine = mine || me.getRole() == Role.MECHANIC;
        if (onlyMine) {
            Mechanic m = mechanics.forUser(me.getId());
            mechanicId = m == null ? -1L : m.getId();
        }
        model.addAttribute("jobs", jobs.search(q, st, mechanicId, active));
        model.addAttribute("q", q);
        model.addAttribute("status", st);
        model.addAttribute("active", active);
        model.addAttribute("mine", onlyMine);
        model.addAttribute("statuses", BookingStatus.values());
        return "admin/jobs/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal GarageUserDetails me, Model model) {
        JobCard job = jobs.get(id);
        Booking b = job.getBooking();
        model.addAttribute("job", job);
        model.addAttribute("booking", b);
        model.addAttribute("inspection", inspections.find(id));
        model.addAttribute("estimates", estimates.forJob(id));
        model.addAttribute("history", estimates.history(id));
        model.addAttribute("payments", payments.forJob(id));
        model.addAttribute("invoice", invoices.forJob(id));
        model.addAttribute("mechanics", mechanics.active());
        model.addAttribute("parts", inventory.activeParts());
        model.addAttribute("nextStatus", b.getStatus().next());
        model.addAttribute("lineTypes", LineType.values());
        model.addAttribute("modes", PaymentMode.values());
        model.addAttribute("channels", ApprovalChannel.values());
        model.addAttribute("lineForm", new JobLineForm());
        model.addAttribute("payForm", new PaymentForm());
        boolean owner = me.getRole() != Role.MECHANIC
                || (job.getMechanic() != null && job.getMechanic().getUser().getId().equals(me.getId()));
        model.addAttribute("canWork", owner);
        boolean editable = b.getStatus().getStep() >= BookingStatus.VEHICLE_RECEIVED.getStep()
                && b.getStatus().getStep() < BookingStatus.READY.getStep();
        model.addAttribute("editable", editable);
        return "admin/jobs/detail";
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        JobCard job = jobs.get(id);
        model.addAttribute("job", job);
        model.addAttribute("inspection", inspections.find(id));
        model.addAttribute("estimates", estimates.forJob(id));
        model.addAttribute("payments", payments.forJob(id));
        return "print/jobcard";
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(Roles.ADVISORS)
    public String assign(@PathVariable Long id, @RequestParam Long mechanicId, RedirectAttributes ra) {
        return run(ra, "Mechanic assigned.", "/admin/jobs/" + id, () -> jobs.assignMechanic(id, mechanicId));
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize(Roles.WORKSHOP)
    public String addLine(@PathVariable Long id, @Valid @ModelAttribute("lineForm") JobLineForm form,
                          BindingResult result, RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/jobs/" + id;
        }
        return run(ra, "Item added to the job (draft - send an estimate to get approval).", "/admin/jobs/" + id,
                () -> jobs.addLine(id, form));
    }

    @PostMapping("/{id}/lines/{lineId}/update")
    @PreAuthorize(Roles.WORKSHOP)
    public String updateLine(@PathVariable Long id, @PathVariable Long lineId, @RequestParam BigDecimal quantity,
                             @RequestParam BigDecimal unitPrice, RedirectAttributes ra) {
        return run(ra, "Item updated.", "/admin/jobs/" + id, () -> jobs.updateLine(id, lineId, quantity, unitPrice));
    }

    @PostMapping("/{id}/lines/{lineId}/remove")
    @PreAuthorize(Roles.WORKSHOP)
    public String removeLine(@PathVariable Long id, @PathVariable Long lineId, RedirectAttributes ra) {
        return run(ra, "Item removed.", "/admin/jobs/" + id, () -> jobs.removeLine(id, lineId));
    }

    @PostMapping("/{id}/estimate")
    @PreAuthorize(Roles.ADVISORS)
    public String sendEstimate(@PathVariable Long id, @RequestParam(required = false) String note, RedirectAttributes ra) {
        return run(ra, "Estimate sent to the customer for approval.", "/admin/jobs/" + id,
                () -> estimates.generateAndSend(id, note));
    }

    /** Staff records the customer's decision received by phone / in person / WhatsApp. */
    @PostMapping("/{id}/estimates/{estimateId}/decide")
    @PreAuthorize(Roles.ADVISORS)
    public String decide(@PathVariable Long id, @PathVariable Long estimateId,
                         @RequestParam(required = false) String action,
                         @RequestParam(required = false) String note,
                         @RequestParam(defaultValue = "PHONE") String channel,
                         @RequestParam Map<String, String> params, RedirectAttributes ra) {
        ApprovalChannel ch = parseEnum(ApprovalChannel.class, channel);
        if (ch == null || ch == ApprovalChannel.PORTAL) {
            ch = ApprovalChannel.PHONE;
        }
        final ApprovalChannel channelFinal = ch;
        return run(ra, "Customer decision recorded.", "/admin/jobs/" + id, () -> {
            Estimate e = estimates.get(estimateId);
            if (!e.getJobCard().getId().equals(id)) {
                throw new BusinessException("Estimate does not belong to this job.");
            }
            Map<Long, ApprovalStatus> decisions = new HashMap<>();
            if ("approveAll".equals(action)) {
                decisions = estimates.allAwaiting(e, ApprovalStatus.APPROVED);
            } else if ("rejectAll".equals(action)) {
                decisions = estimates.allAwaiting(e, ApprovalStatus.REJECTED);
            } else {
                for (Map.Entry<String, String> p : params.entrySet()) {
                    if (p.getKey().startsWith("decision-")) {
                        try {
                            Long lineId = Long.valueOf(p.getKey().substring(9));
                            ApprovalStatus s = parseEnum(ApprovalStatus.class, p.getValue());
                            if (s != null) {
                                decisions.put(lineId, s);
                            }
                        } catch (NumberFormatException ignored) {
                            // skip malformed parameter
                        }
                    }
                }
            }
            estimates.decide(estimateId, decisions, channelFinal,
                    e.getJobCard().getCustomer().getName() + " (recorded by " + CurrentUser.name() + ")", note, null);
        });
    }

    @PostMapping("/{id}/hours")
    @PreAuthorize(Roles.WORKSHOP)
    public String logHours(@PathVariable Long id, @RequestParam BigDecimal hours, RedirectAttributes ra) {
        return run(ra, "Labour hours logged.", "/admin/jobs/" + id, () -> jobs.logHours(id, hours));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize(Roles.ADVISORS)
    public String notes(@PathVariable Long id, @RequestParam(required = false) String notes,
                        @RequestParam(required = false) Integer odometerOut,
                        @RequestParam(required = false) String fuelLevel, RedirectAttributes ra) {
        return run(ra, "Job details saved.", "/admin/jobs/" + id, () -> jobs.saveNotes(id, notes, odometerOut, fuelLevel));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize(Roles.WORKSHOP)
    public String hold(@PathVariable Long id, @RequestParam boolean hold, RedirectAttributes ra) {
        return run(ra, hold ? "Job put on hold." : "Job resumed.", "/admin/jobs/" + id, () -> jobs.setHold(id, hold));
    }

    // ---------------- inspection ----------------

    @GetMapping("/{id}/inspection")
    public String inspection(@PathVariable Long id, @AuthenticationPrincipal GarageUserDetails me, Model model) {
        JobCard job = jobs.get(id);
        Inspection i = inspections.find(id);
        InspectionForm f = new InspectionForm();
        if (i != null) {
            f.setOdometerReading(i.getOdometerReading());
            f.setNotes(i.getNotes());
            f.setDefectiveParts(i.getDefectiveParts());
            f.setRecommendedRepairs(i.getRecommendedRepairs());
            f.setEstimatedCost(i.getEstimatedCost());
            for (InspectionItem it : i.getItems()) {
                InspectionForm.Item x = new InspectionForm.Item();
                x.setArea(it.getArea());
                x.setCondition(it.getCondition());
                x.setRemarks(it.getRemarks());
                f.getItems().add(x);
            }
        }
        model.addAttribute("job", job);
        model.addAttribute("inspection", i);
        model.addAttribute("form", f);
        model.addAttribute("conditions", ItemCondition.values());
        boolean owner = me.getRole() != Role.MECHANIC
                || (job.getMechanic() != null && job.getMechanic().getUser().getId().equals(me.getId()));
        BookingStatus st = job.getBooking().getStatus();
        model.addAttribute("canEdit", owner && i != null && st.getStep() >= BookingStatus.INSPECTION.getStep()
                && st.getStep() <= BookingStatus.QUALITY_CHECK.getStep()
                && me.getRole() != Role.ACCOUNTANT);
        return "admin/jobs/inspection";
    }

    @PostMapping("/{id}/inspection")
    @PreAuthorize(Roles.WORKSHOP)
    public String saveInspection(@PathVariable Long id, @Valid @ModelAttribute("form") InspectionForm form,
                                 BindingResult result, @RequestParam(defaultValue = "save") String action,
                                 RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/jobs/" + id + "/inspection";
        }
        boolean submit = "submit".equals(action);
        return run(ra, submit ? "Inspection report submitted." : "Inspection saved.",
                "/admin/jobs/" + id + "/inspection", () -> inspections.save(id, form, submit));
    }

    @PostMapping("/{id}/inspection/media")
    @PreAuthorize(Roles.WORKSHOP)
    public String upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                         @RequestParam(required = false) String caption, RedirectAttributes ra) {
        return run(ra, "File uploaded.", "/admin/jobs/" + id + "/inspection", () -> inspections.addMedia(id, file, caption));
    }

    @PostMapping("/{id}/inspection/media/{mediaId}/delete")
    @PreAuthorize(Roles.WORKSHOP)
    public String deleteMedia(@PathVariable Long id, @PathVariable Long mediaId, RedirectAttributes ra) {
        return run(ra, "File removed.", "/admin/jobs/" + id + "/inspection", () -> inspections.deleteMedia(mediaId));
    }

    @PostMapping("/{id}/inspection/reopen")
    @PreAuthorize(Roles.WORKSHOP)
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "Inspection re-opened for editing.", "/admin/jobs/" + id + "/inspection", () -> inspections.reopen(id));
    }
}
