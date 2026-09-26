package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.AppUser;
import com.citycarservices.garage.domain.Role;
import com.citycarservices.garage.form.UserForm;
import com.citycarservices.garage.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private static final int MAX_FAILS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AppUser> all() {
        return users.findAllByOrderByFullNameAsc();
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public AppUser create(String fullName, String email, String phone, Role role, String rawPassword,
                          boolean mustChange) {
        String mail = email == null ? "" : email.trim().toLowerCase();
        if (mail.isEmpty()) {
            throw new BusinessException("Email is required.");
        }
        if (users.existsByEmailIgnoreCase(mail)) {
            throw new BusinessException("An account with this email already exists.");
        }
        Validators.password(rawPassword);
        AppUser u = new AppUser();
        u.setFullName(fullName.trim());
        u.setEmail(mail);
        u.setPhone(Validators.blankToNull(phone));
        u.setRole(role);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setMustChangePassword(mustChange);
        u = users.save(u);
        audit.log("USER_CREATED", mail + " as " + role);
        return u;
    }

    @Transactional
    public void update(Long id, UserForm f) {
        AppUser u = get(id);
        String mail = f.getEmail().trim().toLowerCase();
        if (!mail.equalsIgnoreCase(u.getEmail()) && users.existsByEmailIgnoreCase(mail)) {
            throw new BusinessException("Another account already uses this email.");
        }
        if (u.getRole() == Role.CUSTOMER || f.getRole() == Role.CUSTOMER) {
            if (u.getRole() != f.getRole()) {
                throw new BusinessException("Customer accounts are managed from the customer record.");
            }
        }
        boolean wasSuper = u.getRole() == Role.SUPER_ADMIN && u.isEnabled();
        boolean stillSuper = f.getRole() == Role.SUPER_ADMIN && f.isEnabled();
        if (wasSuper && !stillSuper && countEnabledSuperAdmins() <= 1) {
            throw new BusinessException("At least one active Super Admin is required.");
        }
        CurrentUserGuard.notSelfLockout(id, f.isEnabled());
        u.setFullName(f.getFullName().trim());
        u.setEmail(mail);
        u.setPhone(Validators.blankToNull(f.getPhone()));
        u.setRole(f.getRole());
        u.setEnabled(f.isEnabled());
        audit.log("USER_UPDATED", mail + " role=" + f.getRole() + " enabled=" + f.isEnabled());
    }

    private long countEnabledSuperAdmins() {
        return users.findAll().stream().filter(x -> x.getRole() == Role.SUPER_ADMIN && x.isEnabled()).count();
    }

    /** Generates a temporary password and forces a change at next login. Returns the plain password once. */
    @Transactional
    public String resetPassword(Long id) {
        AppUser u = get(id);
        String temp = generatePassword();
        u.setPasswordHash(encoder.encode(temp));
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        audit.log("PASSWORD_RESET", u.getEmail());
        return temp;
    }

    @Transactional
    public void unlock(Long id) {
        AppUser u = get(id);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        audit.log("USER_UNLOCKED", u.getEmail());
    }

    @Transactional
    public void changePassword(Long userId, String current, String next, String confirm) {
        AppUser u = get(userId);
        if (!encoder.matches(current == null ? "" : current, u.getPasswordHash())) {
            throw new BusinessException("Your current password is incorrect.");
        }
        if (next == null || !next.equals(confirm)) {
            throw new BusinessException("New password and confirmation do not match.");
        }
        Validators.password(next);
        if (encoder.matches(next, u.getPasswordHash())) {
            throw new BusinessException("Choose a password different from the current one.");
        }
        u.setPasswordHash(encoder.encode(next));
        u.setMustChangePassword(false);
        audit.log("PASSWORD_CHANGED", u.getEmail());
    }

    @Transactional
    public void recordLoginFailure(String email) {
        if (email == null) {
            return;
        }
        users.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
            int n = u.getFailedAttempts() + 1;
            if (n >= MAX_FAILS) {
                u.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                u.setFailedAttempts(0);
                audit.log("ACCOUNT_LOCKED", u.getEmail() + " locked for " + LOCK_MINUTES + " minutes");
            } else {
                u.setFailedAttempts(n);
            }
        });
    }

    @Transactional
    public void recordLoginSuccess(String email) {
        if (email == null) {
            return;
        }
        users.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            u.setLastLoginAt(LocalDateTime.now());
        });
    }

    public String generatePassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        sb.append(random.nextInt(9) + 1);
        return sb.toString();
    }
}
