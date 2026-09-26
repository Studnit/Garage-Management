package com.citycarservices.garage.web;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.Roles;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.PaymentForm;
import com.citycarservices.garage.service.InvoicePdfService;
import com.citycarservices.garage.service.InvoiceService;
import com.citycarservices.garage.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class AdminBillingController extends BaseController {

    private final InvoiceService invoices;
    private final PaymentService payments;
    private final InvoicePdfService pdf;

    public AdminBillingController(InvoiceService invoices, PaymentService payments, InvoicePdfService pdf) {
        this.invoices = invoices;
        this.payments = payments;
        this.pdf = pdf;
    }

    @GetMapping("/admin/invoices")
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                       Model model) {
        PaymentStatus st = parseEnum(PaymentStatus.class, status);
        model.addAttribute("invoices", invoices.search(q, st));
        model.addAttribute("q", q);
        model.addAttribute("status", st);
        model.addAttribute("statuses", PaymentStatus.values());
        return "admin/invoices/list";
    }

    @GetMapping("/admin/invoices/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Invoice inv = invoices.get(id);
        model.addAttribute("invoice", inv);
        model.addAttribute("payments", payments.forJob(inv.getJobCard().getId()));
        model.addAttribute("payForm", new PaymentForm());
        model.addAttribute("modes", PaymentMode.values());
        return "admin/invoices/detail";
    }

    @GetMapping("/admin/invoices/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        Invoice inv = invoices.get(id);
        model.addAttribute("invoice", inv);
        model.addAttribute("payments", payments.forJob(inv.getJobCard().getId()));
        return "print/invoice";
    }

    @GetMapping("/admin/invoices/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Invoice inv = invoices.get(id);
        return pdfResponse(inv);
    }

    static ResponseEntity<byte[]> pdfResponse(Invoice inv, InvoicePdfService pdf) {
        byte[] bytes = pdf.render(inv);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + inv.getInvoiceNo() + ".pdf\"")
                .body(bytes);
    }

    private ResponseEntity<byte[]> pdfResponse(Invoice inv) {
        return pdfResponse(inv, pdf);
    }

    @PostMapping("/admin/invoices/{id}/terms")
    @PreAuthorize(Roles.ACCOUNTS)
    public String terms(@PathVariable Long id, @RequestParam(required = false) BigDecimal discount,
                        @RequestParam(required = false) String notes,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
                        RedirectAttributes ra) {
        return run(ra, "Invoice updated.", "/admin/invoices/" + id, () -> invoices.updateTerms(id, discount, notes, dueDate));
    }

    /** Records an advance or a payment against the job / invoice. */
    @PostMapping("/admin/payments/job/{jobId}")
    public String record(@PathVariable Long jobId, @Valid @ModelAttribute("payForm") PaymentForm form,
                         BindingResult result, @RequestParam(defaultValue = "job") String back, RedirectAttributes ra) {
        String target = "/admin/jobs/" + jobId;
        if ("invoice".equals(back)) {
            Invoice inv = invoices.forJob(jobId);
            if (inv != null) {
                target = "/admin/invoices/" + inv.getId();
            }
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:" + target;
        }
        return run(ra, "Payment recorded.", target, () -> payments.record(jobId, form));
    }

    @GetMapping("/admin/payments")
    public String pending(Model model) {
        model.addAttribute("payments", payments.pendingVerification());
        return "admin/payments";
    }

    @PostMapping("/admin/payments/{id}/verify")
    @PreAuthorize(Roles.ACCOUNTS)
    public String verify(@PathVariable Long id, @RequestParam boolean accept, RedirectAttributes ra) {
        return run(ra, accept ? "Payment verified and posted." : "Payment rejected.", "/admin/payments",
                () -> payments.verify(id, accept));
    }
}
