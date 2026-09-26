package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.Customer;
import com.citycarservices.garage.domain.Vehicle;
import com.citycarservices.garage.form.VehicleForm;
import com.citycarservices.garage.repo.CustomerRepository;
import com.citycarservices.garage.repo.VehicleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicles;
    private final CustomerRepository customers;
    private final AuditService audit;

    public VehicleService(VehicleRepository vehicles, CustomerRepository customers, AuditService audit) {
        this.vehicles = vehicles;
        this.customers = customers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Vehicle get(Long id) {
        return vehicles.findById(id).orElseThrow(() -> new NotFoundException("Vehicle not found"));
    }

    /** Portal safety: the vehicle must belong to the customer, otherwise it "does not exist". */
    @Transactional(readOnly = true)
    public Vehicle getOwned(Long id, Long customerId) {
        Vehicle v = get(id);
        if (!v.getCustomer().getId().equals(customerId)) {
            throw new NotFoundException("Vehicle not found");
        }
        return v;
    }

    @Transactional(readOnly = true)
    public List<Vehicle> forCustomer(Long customerId) {
        return vehicles.findByCustomerIdOrderByIdAsc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> search(String q) {
        if (q == null || q.isBlank()) {
            return vehicles.findAll(PageRequest.of(0, 300, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        }
        return vehicles.search(q.trim(), PageRequest.of(0, 300));
    }

    @Transactional(readOnly = true)
    public long count() {
        return vehicles.count();
    }

    @Transactional
    public Vehicle create(Long customerId, VehicleForm f) {
        Customer c = customers.findById(customerId).orElseThrow(() -> new NotFoundException("Customer not found"));
        String reg = Validators.regNo(f.getRegNo());
        if (vehicles.existsByRegNo(reg)) {
            throw new BusinessException("A vehicle with registration " + reg + " is already registered.");
        }
        String vin = vin(f.getVin());
        if (vin != null && vehicles.existsByVin(vin)) {
            throw new BusinessException("A vehicle with this VIN / chassis number already exists.");
        }
        Vehicle v = new Vehicle();
        v.setCustomer(c);
        v.setRegNo(reg);
        apply(v, f, vin);
        v = vehicles.save(v);
        audit.log("VEHICLE_CREATED", reg + " for " + c.getName());
        return v;
    }

    @Transactional
    public Vehicle update(Long id, VehicleForm f, Long ownerRestriction) {
        Vehicle v = ownerRestriction == null ? get(id) : getOwned(id, ownerRestriction);
        String reg = Validators.regNo(f.getRegNo());
        vehicles.findByRegNo(reg).ifPresent(o -> {
            if (!o.getId().equals(id)) {
                throw new BusinessException("Another vehicle already uses registration " + reg + ".");
            }
        });
        String vin = vin(f.getVin());
        if (vin != null) {
            vehicles.findAll().stream().filter(o -> vin.equals(o.getVin()) && !o.getId().equals(id)).findAny()
                    .ifPresent(o -> {
                        throw new BusinessException("Another vehicle already uses this VIN / chassis number.");
                    });
        }
        v.setRegNo(reg);
        apply(v, f, vin);
        audit.log("VEHICLE_UPDATED", reg);
        return v;
    }

    private String vin(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toUpperCase().replaceAll("\\s+", "");
    }

    private void apply(Vehicle v, VehicleForm f, String vin) {
        v.setMake(f.getMake().trim());
        v.setModel(f.getModel().trim());
        v.setVariant(Validators.blankToNull(f.getVariant()));
        v.setMfgYear(f.getMfgYear());
        v.setFuelType(f.getFuelType());
        v.setOdometer(f.getOdometer() == null ? 0 : f.getOdometer());
        v.setVin(vin);
        v.setEngineNo(Validators.blankToNull(f.getEngineNo()));
        v.setColor(Validators.blankToNull(f.getColor()));
        v.setInsurer(Validators.blankToNull(f.getInsurer()));
        v.setPolicyNo(Validators.blankToNull(f.getPolicyNo()));
        v.setInsuranceExpiry(f.getInsuranceExpiry());
        v.setPucExpiry(f.getPucExpiry());
        v.setNextServiceDate(f.getNextServiceDate());
        v.setNextServiceKm(f.getNextServiceKm());
        v.setNotes(Validators.blankToNull(f.getNotes()));
    }
}
