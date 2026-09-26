package com.citycarservices.garage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.citycarservices.garage.repo.BookingRepository;
import com.citycarservices.garage.repo.CustomerRepository;
import com.citycarservices.garage.repo.InvoiceRepository;
import com.citycarservices.garage.repo.JobCardRepository;
import com.citycarservices.garage.repo.PartRepository;
import com.citycarservices.garage.repo.VehicleRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end smoke test: boots the whole application on an in-memory database with the demo data, signs in as
 * staff and as a customer, and renders every screen. A template or query error anywhere fails this test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmokeTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private VehicleRepository vehicles;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private JobCardRepository jobs;
    @Autowired
    private InvoiceRepository invoices;
    @Autowired
    private PartRepository parts;

    private MockHttpSession login(String user, String password) throws Exception {
        MvcResult r = mvc.perform(post("/login").param("username", user).param("password", password).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = r.getResponse().getRedirectedUrl();
        assertNotNull(location);
        if (location.contains("error")) {
            throw new AssertionError("Login failed for " + user + " -> " + location);
        }
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private void ok(MockHttpSession s, String url) throws Exception {
        try {
            mvc.perform(get(url).session(s)).andExpect(status().isOk());
        } catch (Throwable t) {
            throw new AssertionError("GET " + url + " failed: " + t, t);
        }
    }

    @Test
    void publicPagesRender() throws Exception {
        for (String url : List.of("/", "/services", "/contact", "/login", "/register", "/actuator/health")) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void securityHeadersAndRedirects() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/portal")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/")).andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(post("/login").param("username", "x").param("password", "y")).andExpect(status().isForbidden());
    }

    @Test
    void badLoginIsRejected() throws Exception {
        mvc.perform(post("/login").param("username", "admin@citycarservices.in").param("password", "wrong-pass1").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Location", "/login?error"));
    }

    @Test
    @Transactional
    void everyStaffScreenRenders() throws Exception {
        MockHttpSession s = login("admin@citycarservices.in", "Admin@12345");
        List<String> urls = new ArrayList<>(List.of(
                "/admin", "/admin/customers", "/admin/customers/new", "/admin/vehicles", "/admin/bookings",
                "/admin/bookings/new", "/admin/jobs", "/admin/jobs?active=true", "/admin/invoices", "/admin/payments",
                "/admin/inventory", "/admin/inventory/new", "/admin/suppliers", "/admin/mechanics", "/admin/mechanics/new",
                "/admin/pickups", "/admin/reminders", "/admin/services", "/admin/services/new", "/admin/users",
                "/admin/users/new", "/admin/audit", "/admin/feedback", "/account/password"));
        for (String[] r : com.citycarservices.garage.service.ReportService.CATALOG) {
            urls.add("/admin/reports/" + r[0]);
        }
        customers.findAll().forEach(c -> {
            urls.add("/admin/customers/" + c.getId());
            urls.add("/admin/customers/" + c.getId() + "/edit");
            urls.add("/admin/bookings/new?customerId=" + c.getId());
        });
        vehicles.findAll().forEach(v -> {
            urls.add("/admin/vehicles/" + v.getId());
            urls.add("/admin/vehicles/" + v.getId() + "/edit");
            urls.add("/admin/vehicles/new?customerId=" + v.getCustomer().getId());
        });
        bookings.findAll().forEach(b -> urls.add("/admin/bookings/" + b.getId()));
        jobs.findAll().forEach(j -> {
            urls.add("/admin/jobs/" + j.getId());
            urls.add("/admin/jobs/" + j.getId() + "/inspection");
            urls.add("/admin/jobs/" + j.getId() + "/print");
        });
        invoices.findAll().forEach(i -> {
            urls.add("/admin/invoices/" + i.getId());
            urls.add("/admin/invoices/" + i.getId() + "/print");
        });
        parts.findAll().forEach(p -> {
            urls.add("/admin/inventory/" + p.getId());
            urls.add("/admin/inventory/" + p.getId() + "/edit");
        });
        for (String u : urls) {
            ok(s, u);
        }
        // invoice PDFs
        for (var i : invoices.findAll()) {
            mvc.perform(get("/admin/invoices/" + i.getId() + "/pdf").session(s)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"));
        }
    }

    @Test
    @Transactional
    void everyCustomerPortalScreenRenders() throws Exception {
        MockHttpSession s = login("rahul@example.com", "Customer@123");
        List<String> urls = new ArrayList<>(List.of("/portal", "/portal/vehicles", "/portal/vehicles/new", "/portal/bookings",
                "/portal/bookings/new", "/portal/invoices", "/portal/history", "/portal/profile", "/account/password"));
        var mine = customers.findAll().stream().filter(c -> "rahul@example.com".equals(c.getEmail())).findFirst().orElseThrow();
        vehicles.findAll().stream().filter(v -> v.getCustomer().getId().equals(mine.getId())).forEach(v -> {
            urls.add("/portal/vehicles/" + v.getId());
            urls.add("/portal/vehicles/" + v.getId() + "/edit");
        });
        bookings.findAll().stream().filter(b -> b.getCustomer().getId().equals(mine.getId()))
                .forEach(b -> urls.add("/portal/bookings/" + b.getId()));
        invoices.findAll().stream().filter(i -> i.getCustomer().getId().equals(mine.getId())).forEach(i -> {
            urls.add("/portal/invoices/" + i.getId());
            urls.add("/portal/invoices/" + i.getId() + "/print");
        });
        for (String u : urls) {
            ok(s, u);
        }
        // a customer must never reach staff screens
        mvc.perform(get("/admin/customers").session(s)).andExpect(status().isForbidden());
    }
}
