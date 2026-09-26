package com.citycarservices.garage.config;

import com.citycarservices.garage.domain.AppUser;
import com.citycarservices.garage.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Immutable principal stored in the session. */
public class GarageUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String password;
    private final String fullName;
    private final Role role;
    private final boolean enabled;
    private final boolean locked;

    public GarageUserDetails(AppUser u) {
        this.id = u.getId();
        this.email = u.getEmail();
        this.password = u.getPasswordHash();
        this.fullName = u.getFullName();
        this.role = u.getRole();
        this.enabled = u.isEnabled();
        this.locked = u.isLocked();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isStaff() {
        return role.isStaff();
    }

    public boolean hasRole(Role r) {
        return role == r;
    }

    public boolean isManagement() {
        return role == Role.SUPER_ADMIN || role == Role.GARAGE_MANAGER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
