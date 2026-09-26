package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.MechanicForm;
import com.citycarservices.garage.repo.MechanicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MechanicService {

    private final MechanicRepository mechanics;
    private final UserService users;
    private final JobService jobs;
    private final AuditService audit;

    public MechanicService(MechanicRepository mechanics, UserService users, JobService jobs, AuditService audit) {
        this.mechanics = mechanics;
        this.users = users;
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Mechanic get(Long id) {
        return mechanics.findById(id).orElseThrow(() -> new NotFoundException("Mechanic not found"));
    }

    @Transactional(readOnly = true)
    public Mechanic forUser(Long userId) {
        return mechanics.findByUserId(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Mechanic> all() {
        return mechanics.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<Mechanic> active() {
        return mechanics.findByActiveTrueOrderByIdAsc();
    }

    /** Creates the mechanic's login and profile. Returns the temporary password if one was generated, else null. */
    @Transactional
    public String create(MechanicForm f) {
        boolean generated = f.getPassword() == null || f.getPassword().isBlank();
        String pw = generated ? users.generatePassword() : f.getPassword();
        AppUser u = users.create(f.getFullName(), f.getEmail(), f.getPhone(), Role.MECHANIC, pw, generated);
        Mechanic m = new Mechanic();
        m.setUser(u);
        apply(m, f);
        m = mechanics.save(m);
        m.setEmployeeCode(f.getEmployeeCode() != null && !f.getEmployeeCode().isBlank()
                ? f.getEmployeeCode().trim().toUpperCase() : String.format("MECH-%03d", m.getId()));
        audit.log("MECHANIC_CREATED", m.getName());
        return generated ? pw : null;
    }

    @Transactional
    public void update(Long id, MechanicForm f) {
        Mechanic m = get(id);
        AppUser u = m.getUser();
        String mail = f.getEmail().trim().toLowerCase();
        if (!mail.equalsIgnoreCase(u.getEmail())) {
            throw new BusinessException("To change the login email, use Users (Super Admin).");
        }
        u.setFullName(f.getFullName().trim());
        u.setPhone(Validators.blankToNull(f.getPhone()));
        u.setEnabled(f.isActive());
        apply(m, f);
        if (f.getEmployeeCode() != null && !f.getEmployeeCode().isBlank()) {
            String code = f.getEmployeeCode().trim().toUpperCase();
            if (!code.equals(m.getEmployeeCode()) && mechanics.existsByEmployeeCode(code)) {
                throw new BusinessException("Employee code " + code + " is already used.");
            }
            m.setEmployeeCode(code);
        }
        audit.log("MECHANIC_UPDATED", m.getName());
    }

    private void apply(Mechanic m, MechanicForm f) {
        m.setSkills(Validators.blankToNull(f.getSkills()));
        m.setSpecialization(Validators.blankToNull(f.getSpecialization()));
        m.setExperienceYears(f.getExperienceYears());
        m.setActive(f.isActive());
    }

    @Transactional(readOnly = true)
    public List<MechanicStats> allStats(LocalDateTime from, LocalDateTime to) {
        List<MechanicStats> out = new ArrayList<>();
        for (Mechanic m : mechanics.findAllByOrderByIdAsc()) {
            out.add(stats(m, from, to));
        }
        return out;
    }

    /** Completed-job metrics are limited to the period when from/to are given. */
    @Transactional(readOnly = true)
    public MechanicStats stats(Mechanic m, LocalDateTime from, LocalDateTime to) {
        int open = 0;
        int done = 0;
        BigDecimal est = Money.ZERO;
        BigDecimal act = Money.ZERO;
        BigDecimal labour = Money.ZERO;
        long turnaroundMinutes = 0;
        for (JobCard j : jobs.forMechanic(m.getId())) {
            if (j.getBooking().getStatus().isInGarage() && j.getStatus() != JobStatus.COMPLETED) {
                open++;
            }
            boolean completed = j.getCompletedAt() != null;
            if (!completed) {
                continue;
            }
            if ((from != null && j.getCompletedAt().isBefore(from)) || (to != null && j.getCompletedAt().isAfter(to))) {
                continue;
            }
            done++;
            act = act.add(j.getActualHours());
            turnaroundMinutes += Duration.between(j.getCreatedAt(), j.getCompletedAt()).toMinutes();
            for (JobLine l : j.getLines()) {
                if (l.getApproval() == ApprovalStatus.APPROVED && l.getLineType() != LineType.PART) {
                    labour = labour.add(l.getTaxable());
                    if (l.getLabourHours() != null) {
                        est = est.add(l.getLabourHours());
                    }
                }
            }
        }
        int pct = act.signum() > 0 ? est.multiply(new BigDecimal("100")).divide(act, 0, RoundingMode.HALF_UP).intValue() : 0;
        double avg = done == 0 ? 0 : Math.round(turnaroundMinutes / 60.0 / done * 10) / 10.0;
        return new MechanicStats(m, open, done, est, act, labour, pct, avg);
    }
}
