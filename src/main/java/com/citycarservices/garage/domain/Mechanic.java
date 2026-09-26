package com.citycarservices.garage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mechanic")
@Getter
@Setter
public class Mechanic extends BaseEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private AppUser user;

    @Column(unique = true, length = 20)
    private String employeeCode;

    /** Comma separated skills, e.g. "Engine, AC, Electrical". */
    @Column(length = 400)
    private String skills;

    @Column(length = 100)
    private String specialization;

    private int experienceYears;

    private boolean active = true;

    public String getName() {
        return user.getFullName();
    }

    public String getPhone() {
        return user.getPhone();
    }

    public List<String> getSkillList() {
        List<String> out = new ArrayList<>();
        if (skills != null) {
            for (String s : skills.split(",")) {
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }
}
