package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.Fmt;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.PaymentForm;
import com.citycarservices.garage.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final JobService jobService;
    private final InvoiceService invoiceService;
    private final NumberService numbers;
    private final AuditService audit;
    private final Fmt fmt = new Fmt();

    public PaymentService(PaymentRepository payments, JobService jobService, InvoiceService invoiceService,
                          NumberService numbers, AuditService audit) {
        this.payments = payments;
        this.jobService = jobService;
        this.invoiceService = invoiceService;
        this.numbers = numbers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Payment> forJob(Long jobId) {
        return payments.findByJobCardIdOrderByPaidAtAscIdAsc(jobId);
    }

    @Transactional(readOnly = true)
    public List<Payment> pendingVerification() {
        return payments.findByStatusOrderByPaidAtDesc(PaymentRecordStatus.PENDING_VERIFICATION);
    }

    /** Staff records cash/UPI/card/bank payment, either an advance on the job or a payment against the invoice. */
    @Transactional
    public Payment record(Long jobId, PaymentForm f) {
        JobCard job = jobService.get(jobId);
        if (job.getBooking().getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("The booking is cancelled.");
        }
        Invoice inv = invoiceService.forJob(jobId);
        BigDecimal amount = Money.round(f.getAmount());
        if (inv != null && amount.compareTo(inv.getBalance()) > 0) {
            throw new BusinessException("Amount exceeds the balance due (" + fmt.money(inv.getBalance()) + ").");
        }
        if ((f.getMode() == PaymentMode.UPI || f.getMode() == PaymentMode.BANK_TRANSFER)
                && (f.getReference() == null || f.getReference().isBlank())) {
            throw new BusinessException("Enter the transaction / UTR reference for " + f.getMode().getLabel() + " payments.");
        }
        Payment p = new Payment();
        p.setReceiptNo(numbers.next("RCT"));
        p.setJobCard(job);
        p.setInvoice(inv);
        p.setAmount(amount);
        p.setMode(f.getMode());
        p.setReference(Validators.blankToNull(f.getReference()));
        p.setAdvance(inv == null || f.isAdvance());
        p.setStatus(PaymentRecordStatus.RECEIVED);
        p.setReceivedBy(CurrentUser.name());
        p.setPaidAt(LocalDateTime.now());
        p.setNote(Validators.blankToNull(f.getNote()));
        p = payments.save(p);
        if (inv != null) {
            invoiceService.refreshPaid(inv);
        }
        audit.log("PAYMENT_RECORDED", p.getReceiptNo() + " " + fmt.money(amount) + " " + f.getMode());
        return p;
    }

    /** Customer reports an online (UPI/bank) payment; an accountant verifies it before it counts. */
    @Transactional
    public Payment submitOnline(Long invoiceId, Long customerId, BigDecimal amount, PaymentMode mode, String reference) {
        Invoice inv = invoiceService.getOwned(invoiceId, customerId);
        if (inv.isPaid()) {
            throw new BusinessException("This invoice is already fully paid.");
        }
        if (mode == PaymentMode.CASH) {
            throw new BusinessException("Cash is paid at the garage. Choose UPI, card or bank transfer.");
        }
        if (reference == null || reference.isBlank()) {
            throw new BusinessException("Enter the transaction reference (UPI ref / UTR) so we can verify it.");
        }
        BigDecimal a = Money.round(amount);
        if (a.signum() <= 0 || a.compareTo(inv.getBalance()) > 0) {
            throw new BusinessException("Enter an amount up to the balance due (" + fmt.money(inv.getBalance()) + ").");
        }
        Payment p = new Payment();
        p.setJobCard(inv.getJobCard());
        p.setInvoice(inv);
        p.setAmount(a);
        p.setMode(mode);
        p.setReference(reference.trim());
        p.setStatus(PaymentRecordStatus.PENDING_VERIFICATION);
        p.setReceivedBy(CurrentUser.name());
        p.setPaidAt(LocalDateTime.now());
        p.setNote("Submitted by customer from portal");
        p = payments.save(p);
        audit.log("PAYMENT_SUBMITTED", inv.getInvoiceNo() + " " + fmt.money(a) + " ref " + reference);
        return p;
    }

    @Transactional
    public void verify(Long paymentId, boolean accept) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment not found"));
        if (p.getStatus() != PaymentRecordStatus.PENDING_VERIFICATION) {
            throw new BusinessException("This payment has already been processed.");
        }
        Invoice inv = p.getInvoice();
        if (accept) {
            if (inv != null && p.getAmount().compareTo(inv.getBalance()) > 0) {
                throw new BusinessException("Amount is more than the balance due. Reject it or ask the customer to resubmit.");
            }
            p.setStatus(PaymentRecordStatus.RECEIVED);
            p.setReceiptNo(numbers.next("RCT"));
            p.setNote((p.getNote() == null ? "" : p.getNote() + ". ") + "Verified by " + CurrentUser.name());
        } else {
            p.setStatus(PaymentRecordStatus.REJECTED);
            p.setNote((p.getNote() == null ? "" : p.getNote() + ". ") + "Rejected by " + CurrentUser.name());
        }
        if (inv != null) {
            invoiceService.refreshPaid(inv);
        }
        audit.log(accept ? "PAYMENT_VERIFIED" : "PAYMENT_REJECTED", String.valueOf(p.getReference()));
    }
}
