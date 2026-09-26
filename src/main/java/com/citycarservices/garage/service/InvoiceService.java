package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.repo.InvoiceRepository;
import com.citycarservices.garage.repo.PaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** GST invoice generation (intra-state: CGST + SGST) and payment status tracking. */
@Service
public class InvoiceService {

    private static final BigDecimal TWO = new BigDecimal("2");

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final NumberService numbers;
    private final AuditService audit;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, NumberService numbers,
                          AuditService audit) {
        this.invoices = invoices;
        this.payments = payments;
        this.numbers = numbers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Invoice get(Long id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("Invoice not found"));
    }

    @Transactional(readOnly = true)
    public Invoice getOwned(Long id, Long customerId) {
        Invoice i = get(id);
        if (!i.getCustomer().getId().equals(customerId)) {
            throw new NotFoundException("Invoice not found");
        }
        return i;
    }

    @Transactional(readOnly = true)
    public Invoice forJob(Long jobId) {
        return invoices.findByJobCardId(jobId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Invoice> forCustomer(Long customerId) {
        return invoices.findByCustomerIdOrderByInvoiceDateDescIdDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> search(String q, PaymentStatus status) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return invoices.findAll(PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "id"))).getContent().stream()
                .filter(i -> status == null || i.getPaymentStatus() == status)
                .filter(i -> needle.isEmpty()
                        || i.getInvoiceNo().toLowerCase(Locale.ROOT).contains(needle)
                        || i.getVehicle().getRegNo().toLowerCase(Locale.ROOT).contains(needle)
                        || i.getCustomer().getName().toLowerCase(Locale.ROOT).contains(needle)
                        || i.getCustomer().getMobile().contains(needle))
                .toList();
    }

    @Transactional
    public Invoice generateForJob(JobCard job) {
        Invoice existing = invoices.findByJobCardId(job.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        List<JobLine> approved = job.getLines().stream()
                .filter(l -> l.getApproval() == ApprovalStatus.APPROVED).toList();
        if (approved.isEmpty()) {
            throw new BusinessException("There are no approved items to invoice.");
        }
        Invoice inv = new Invoice();
        inv.setInvoiceNo(numbers.next("INV"));
        inv.setJobCard(job);
        inv.setCustomer(job.getCustomer());
        inv.setVehicle(job.getVehicle());
        inv.setInvoiceDate(LocalDate.now());
        inv.setDueDate(LocalDate.now());
        inv.setCreatedBy(CurrentUser.name());
        for (JobLine l : approved) {
            InvoiceLine il = new InvoiceLine();
            il.setInvoice(inv);
            il.setLineType(l.getLineType());
            il.setDescription(l.getDescription());
            il.setHsnSac(l.getHsnSac() != null ? l.getHsnSac()
                    : (l.getLineType() == LineType.PART ? null : "9987"));
            il.setQuantity(l.getQuantity());
            il.setUnitPrice(l.getUnitPrice());
            il.setGstRate(l.getGstRate());
            il.setAmount(l.getTaxable());
            inv.getLines().add(il);
        }
        recalculate(inv);
        inv = invoices.save(inv);
        job.setInvoice(inv);
        for (Payment p : payments.findByJobCardIdOrderByPaidAtAscIdAsc(job.getId())) {
            p.setInvoice(inv);
        }
        refreshPaid(inv);
        audit.log("INVOICE_CREATED", inv.getInvoiceNo() + " total " + inv.getTotal());
        return inv;
    }

    @Transactional
    public void updateTerms(Long invoiceId, BigDecimal discount, String notes, LocalDate dueDate) {
        Invoice inv = get(invoiceId);
        if (inv.getJobCard().getBooking().getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessException("The job is completed; its invoice can no longer be changed.");
        }
        BigDecimal d = Money.round(discount == null ? BigDecimal.ZERO : discount);
        if (d.signum() < 0) {
            throw new BusinessException("Discount cannot be negative.");
        }
        if (d.compareTo(inv.getSubtotal()) > 0) {
            throw new BusinessException("Discount cannot exceed the invoice subtotal.");
        }
        BigDecimal oldDiscount = inv.getDiscount();
        inv.setDiscount(d);
        recalculate(inv);
        if (inv.getTotal().compareTo(inv.getAmountPaid()) < 0) {
            inv.setDiscount(oldDiscount);
            recalculate(inv);
            throw new BusinessException("That discount would make the total lower than the amount already received.");
        }
        inv.setNotes(Validators.blankToNull(notes));
        inv.setDueDate(dueDate == null ? inv.getDueDate() : dueDate);
        refreshPaid(inv);
        audit.log("INVOICE_UPDATED", inv.getInvoiceNo() + " discount " + d);
    }

    /** Recomputes line-level GST (CGST/SGST), discount apportionment, round-off and totals. */
    void recalculate(Invoice inv) {
        BigDecimal subtotal = Money.ZERO;
        for (InvoiceLine l : inv.getLines()) {
            subtotal = subtotal.add(l.getAmount());
        }
        BigDecimal discount = Money.round(inv.getDiscount());
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal allocated = Money.ZERO;
        BigDecimal taxable = Money.ZERO;
        BigDecimal cgst = Money.ZERO;
        BigDecimal sgst = Money.ZERO;
        List<InvoiceLine> ls = inv.getLines();
        for (int i = 0; i < ls.size(); i++) {
            InvoiceLine l = ls.get(i);
            BigDecimal share;
            if (subtotal.signum() == 0) {
                share = Money.ZERO;
            } else if (i == ls.size() - 1) {
                share = discount.subtract(allocated);
            } else {
                share = Money.round(discount.multiply(l.getAmount()).divide(subtotal, 4, RoundingMode.HALF_UP));
            }
            if (share.compareTo(l.getAmount()) > 0) {
                share = l.getAmount();
            }
            allocated = allocated.add(share);
            BigDecimal tv = l.getAmount().subtract(share);
            BigDecimal half = Money.percent(tv, l.getGstRate().divide(TWO, 4, RoundingMode.HALF_UP));
            l.setDiscountShare(share);
            l.setTaxableValue(tv);
            l.setCgst(half);
            l.setSgst(half);
            l.setLineTotal(tv.add(half).add(half));
            taxable = taxable.add(tv);
            cgst = cgst.add(half);
            sgst = sgst.add(half);
        }
        BigDecimal raw = taxable.add(cgst).add(sgst);
        BigDecimal total = raw.setScale(0, RoundingMode.HALF_UP).setScale(2);
        inv.setSubtotal(subtotal);
        inv.setDiscount(discount);
        inv.setTaxableValue(taxable);
        inv.setCgst(cgst);
        inv.setSgst(sgst);
        inv.setTotal(total);
        inv.setRoundOff(total.subtract(raw));
    }

    /** Re-sums received payments and updates the payment status. */
    @Transactional
    public void refreshPaid(Invoice inv) {
        BigDecimal paid = Money.ZERO;
        for (Payment p : payments.findByJobCardIdOrderByPaidAtAscIdAsc(inv.getJobCard().getId())) {
            if (p.getStatus() == PaymentRecordStatus.RECEIVED) {
                paid = paid.add(p.getAmount());
            }
        }
        inv.setAmountPaid(paid);
        if (inv.getTotal().signum() > 0 && paid.compareTo(inv.getTotal()) >= 0) {
            inv.setPaymentStatus(PaymentStatus.PAID);
        } else if (paid.signum() > 0) {
            inv.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            inv.setPaymentStatus(PaymentStatus.UNPAID);
        }
    }
}
