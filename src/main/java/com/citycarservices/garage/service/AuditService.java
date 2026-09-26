package com.citycarservices.garage.service;

import com.citycarservices.garage.domain.AuditLog;
import com.citycarservices.garage.repo.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void log(String action, String detail) {
        AuditLog a = new AuditLog();
        a.setActor(CurrentUser.name());
        a.setAction(action);
        a.setDetail(detail == null ? null : (detail.length() > 990 ? detail.substring(0, 990) : detail));
        repo.save(a);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent(int max) {
        return repo.findAll(PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "id"))).getContent();
    }
}
