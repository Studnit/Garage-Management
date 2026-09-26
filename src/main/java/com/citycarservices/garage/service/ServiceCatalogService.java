package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.Money;
import com.citycarservices.garage.domain.ServiceItem;
import com.citycarservices.garage.form.ServiceItemForm;
import com.citycarservices.garage.repo.ServiceItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceCatalogService {

    private final ServiceItemRepository items;
    private final AuditService audit;

    public ServiceCatalogService(ServiceItemRepository items, AuditService audit) {
        this.items = items;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ServiceItem> active() {
        return items.findByActiveTrueOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ServiceItem> all() {
        return items.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public ServiceItem get(Long id) {
        return items.findById(id).orElseThrow(() -> new NotFoundException("Service not found"));
    }

    @Transactional
    public ServiceItem save(Long id, ServiceItemForm f) {
        ServiceItem s;
        String name = f.getName().trim();
        if (id == null) {
            if (items.existsByName(name)) {
                throw new BusinessException("A service with this name already exists.");
            }
            s = new ServiceItem();
        } else {
            s = get(id);
            items.findByName(name).ifPresent(o -> {
                if (!o.getId().equals(id)) {
                    throw new BusinessException("A service with this name already exists.");
                }
            });
        }
        s.setName(name);
        s.setCategory(f.getCategory());
        s.setDescription(Validators.blankToNull(f.getDescription()));
        s.setIcon(Validators.blankToNull(f.getIcon()));
        s.setBasePrice(Money.round(f.getBasePrice()));
        s.setGstRate(f.getGstRate());
        s.setEstimatedHours(f.getEstimatedHours());
        s.setIntervalKm(f.getIntervalKm() == null || f.getIntervalKm() == 0 ? null : f.getIntervalKm());
        s.setIntervalMonths(f.getIntervalMonths() == null || f.getIntervalMonths() == 0 ? null : f.getIntervalMonths());
        s.setActive(f.isActive());
        s.setSortOrder(f.getSortOrder());
        s = items.save(s);
        audit.log("SERVICE_SAVED", s.getName());
        return s;
    }
}
