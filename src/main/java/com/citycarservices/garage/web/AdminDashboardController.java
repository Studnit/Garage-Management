package com.citycarservices.garage.web;

import com.citycarservices.garage.config.GarageUserDetails;
import com.citycarservices.garage.domain.JobCard;
import com.citycarservices.garage.domain.Mechanic;
import com.citycarservices.garage.domain.Role;
import com.citycarservices.garage.service.DashboardService;
import com.citycarservices.garage.service.JobService;
import com.citycarservices.garage.service.MechanicService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class AdminDashboardController extends BaseController {

    private final DashboardService dashboard;
    private final MechanicService mechanics;
    private final JobService jobs;

    public AdminDashboardController(DashboardService dashboard, MechanicService mechanics, JobService jobs) {
        this.dashboard = dashboard;
        this.mechanics = mechanics;
        this.jobs = jobs;
    }

    @GetMapping({"/admin", "/admin/"})
    public String dashboard(@AuthenticationPrincipal GarageUserDetails me, Model model) {
        model.addAttribute("d", dashboard.build());
        if (me.getRole() == Role.MECHANIC) {
            Mechanic m = mechanics.forUser(me.getId());
            List<JobCard> mine = m == null ? List.of() : jobs.forMechanic(m.getId()).stream()
                    .filter(j -> j.getBooking().getStatus().isInGarage()).toList();
            model.addAttribute("myJobs", mine);
        }
        return "admin/dashboard";
    }
}
