package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.PartForm;
import com.citycarservices.garage.form.SupplierForm;
import com.citycarservices.garage.repo.PartRepository;
import com.citycarservices.garage.repo.StockMovementRepository;
import com.citycarservices.garage.repo.SupplierRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class InventoryService {

    private final PartRepository parts;
    private final StockMovementRepository movements;
    private final SupplierRepository suppliers;
    private final AuditService audit;

    public InventoryService(PartRepository parts, StockMovementRepository movements, SupplierRepository suppliers,
                            AuditService audit) {
        this.parts = parts;
        this.movements = movements;
        this.suppliers = suppliers;
        this.audit = audit;
    }

    // ---------- parts catalogue ----------

    @Transactional(readOnly = true)
    public Part get(Long id) {
        return parts.findById(id).orElseThrow(() -> new NotFoundException("Part not found"));
    }

    @Transactional(readOnly = true)
    public List<Part> search(String q, boolean lowOnly) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return parts.findAll().stream()
                .filter(p -> !lowOnly || (p.isActive() && p.isLowStock()))
                .filter(p -> needle.isEmpty()
                        || p.getPartNo().toLowerCase(Locale.ROOT).contains(needle)
                        || p.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || (p.getBrand() != null && p.getBrand().toLowerCase(Locale.ROOT).contains(needle))
                        || (p.getCategory() != null && p.getCategory().toLowerCase(Locale.ROOT).contains(needle)))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Part> activeParts() {
        return parts.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Part> lowStock() {
        return parts.findLowStock();
    }

    @Transactional
    public Part create(PartForm f) {
        String no = f.getPartNo().trim().toUpperCase(Locale.ROOT);
        if (parts.existsByPartNo(no)) {
            throw new BusinessException("Part number " + no + " already exists.");
        }
        Part p = new Part();
        p.setPartNo(no);
        apply(p, f);
        BigDecimal opening = Money.round(f.getOpeningStock());
        p.setStock(opening);
        p = parts.save(p);
        if (opening.signum() > 0) {
            move(p, StockMovementType.IN, opening, p.getPurchasePrice(), "OPENING", null, null, "Opening stock");
        }
        audit.log("PART_CREATED", no + " " + p.getName());
        return p;
    }

    @Transactional
    public Part update(Long id, PartForm f) {
        Part p = get(id);
        String no = f.getPartNo().trim().toUpperCase(Locale.ROOT);
        parts.findByPartNo(no).ifPresent(o -> {
            if (!o.getId().equals(id)) {
                throw new BusinessException("Part number " + no + " already exists.");
            }
        });
        p.setPartNo(no);
        apply(p, f);
        audit.log("PART_UPDATED", no);
        return p;
    }

    private void apply(Part p, PartForm f) {
        p.setName(f.getName().trim());
        p.setBrand(Validators.blankToNull(f.getBrand()));
        p.setCategory(Validators.blankToNull(f.getCategory()));
        p.setCompatibility(Validators.blankToNull(f.getCompatibility()));
        p.setPurchasePrice(Money.round(f.getPurchasePrice()));
        p.setSellingPrice(Money.round(f.getSellingPrice()));
        p.setGstRate(f.getGstRate());
        p.setHsnCode(Validators.blankToNull(f.getHsnCode()));
        p.setMinStock(Money.round(f.getMinStock()));
        p.setUnit(f.getUnit() == null || f.getUnit().isBlank() ? "Nos" : f.getUnit().trim());
        p.setRackLocation(Validators.blankToNull(f.getRackLocation()));
        p.setActive(f.isActive());
        p.setSupplier(f.getSupplierId() == null ? null
                : suppliers.findById(f.getSupplierId()).orElse(null));
    }

    // ---------- stock movements ----------

    @Transactional(readOnly = true)
    public List<StockMovement> movementsFor(Long partId) {
        return movements.findByPartIdOrderByCreatedAtDescIdDesc(partId, PageRequest.of(0, 100));
    }

    @Transactional
    public void stockIn(Long partId, BigDecimal qty, BigDecimal unitCost, Long supplierId, String reference,
                        String note) {
        requirePositive(qty);
        Part p = parts.lockById(partId).orElseThrow(() -> new NotFoundException("Part not found"));
        Supplier s = supplierId == null ? null : suppliers.findById(supplierId).orElse(null);
        if (unitCost != null && unitCost.signum() > 0) {
            p.setPurchasePrice(Money.round(unitCost));
        }
        if (s != null && p.getSupplier() == null) {
            p.setSupplier(s);
        }
        p.setStock(p.getStock().add(Money.round(qty)));
        move(p, StockMovementType.IN, Money.round(qty), unitCost, reference, null, s, note);
        audit.log("STOCK_IN", p.getPartNo() + " +" + qty);
    }

    @Transactional
    public void stockOut(Long partId, BigDecimal qty, String reference, String note) {
        requirePositive(qty);
        Part p = parts.lockById(partId).orElseThrow(() -> new NotFoundException("Part not found"));
        if (p.getStock().compareTo(qty) < 0) {
            throw new BusinessException("Only " + p.getStock().stripTrailingZeros().toPlainString()
                    + " in stock for " + p.getName() + ".");
        }
        p.setStock(p.getStock().subtract(Money.round(qty)));
        move(p, StockMovementType.OUT, Money.round(qty), p.getPurchasePrice(), reference, null, null, note);
        audit.log("STOCK_OUT", p.getPartNo() + " -" + qty);
    }

    private void requirePositive(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException("Quantity must be greater than zero.");
        }
    }

    private void move(Part p, StockMovementType type, BigDecimal qty, BigDecimal cost, String ref, JobCard job,
                      Supplier supplier, String note) {
        StockMovement m = new StockMovement();
        m.setPart(p);
        m.setType(type);
        m.setQuantity(qty);
        m.setUnitCost(cost == null ? null : Money.round(cost));
        m.setBalanceAfter(p.getStock());
        m.setReference(ref);
        m.setJobCard(job);
        m.setSupplier(supplier);
        m.setNote(note);
        m.setCreatedBy(CurrentUser.name());
        movements.save(m);
    }

    /**
     * Deducts stock for approved part lines that have not been issued yet.
     *
     * @param strict when true an out-of-stock part raises a BusinessException; otherwise the line is skipped
     * @return number of lines issued
     */
    @Transactional
    public int issueForJob(JobCard job, boolean strict) {
        int issued = 0;
        for (JobLine l : job.getLines()) {
            if (l.getLineType() != LineType.PART || l.getApproval() != ApprovalStatus.APPROVED || l.isIssued()) {
                continue;
            }
            if (l.getPart() == null) {
                l.setIssued(true); // free-text / outside purchase, no stock tracking
                continue;
            }
            Part p = parts.lockById(l.getPart().getId()).orElseThrow(() -> new NotFoundException("Part not found"));
            if (p.getStock().compareTo(l.getQuantity()) < 0) {
                if (strict) {
                    throw new BusinessException("Not enough stock for " + p.getName() + " (available "
                            + p.getStock().stripTrailingZeros().toPlainString() + ", required "
                            + l.getQuantity().stripTrailingZeros().toPlainString()
                            + "). Add stock in Inventory first.");
                }
                continue;
            }
            p.setStock(p.getStock().subtract(l.getQuantity()));
            move(p, StockMovementType.OUT, l.getQuantity(), p.getPurchasePrice(), job.getJobNo(), job, null,
                    "Issued to job card");
            l.setIssued(true);
            issued++;
        }
        return issued;
    }

    // ---------- suppliers ----------

    @Transactional(readOnly = true)
    public List<Supplier> suppliers() {
        return suppliers.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Supplier supplier(Long id) {
        return suppliers.findById(id).orElseThrow(() -> new NotFoundException("Supplier not found"));
    }

    @Transactional
    public Supplier saveSupplier(Long id, SupplierForm f) {
        Supplier s;
        if (id == null) {
            if (suppliers.existsByName(f.getName().trim())) {
                throw new BusinessException("A supplier with this name already exists.");
            }
            s = new Supplier();
        } else {
            s = supplier(id);
        }
        s.setName(f.getName().trim());
        s.setContactPerson(Validators.blankToNull(f.getContactPerson()));
        s.setPhone(Validators.blankToNull(f.getPhone()));
        s.setEmail(Validators.blankToNull(f.getEmail()));
        s.setGstin(f.getGstin() == null ? null : f.getGstin().trim().toUpperCase(Locale.ROOT));
        s.setAddress(Validators.blankToNull(f.getAddress()));
        s.setActive(f.isActive());
        s = suppliers.save(s);
        audit.log("SUPPLIER_SAVED", s.getName());
        return s;
    }
}
