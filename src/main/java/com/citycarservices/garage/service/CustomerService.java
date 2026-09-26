package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.GarageProperties;
import com.citycarservices.garage.config.NotFoundException;
import com.citycarservices.garage.domain.AppUser;
import com.citycarservices.garage.domain.Customer;
import com.citycarservices.garage.domain.Role;
import com.citycarservices.garage.form.CustomerForm;
import com.citycarservices.garage.form.RegisterForm;
import com.citycarservices.garage.repo.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final UserService users;
    private final AuditService audit;
    private final GarageProperties props;

    public CustomerService(CustomerRepository customers, UserService users, AuditService audit,
                           GarageProperties props) {
        this.customers = customers;
        this.users = users;
        this.audit = audit;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    @Transactional(readOnly = true)
    public Customer forUser(Long userId) {
        return customers.findByUserId(userId).orElseThrow(() -> new NotFoundException("Customer profile not found"));
    }

    @Transactional(readOnly = true)
    public List<Customer> search(String q) {
        if (q == null || q.isBlank()) {
            return customers.findAll(PageRequest.of(0, 300, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        }
        return customers.search(q.trim(), PageRequest.of(0, 300));
    }

    @Transactional(readOnly = true)
    public long count() {
        return customers.count();
    }

    /** Self registration from the public site: creates the login and the customer profile together. */
    @Transactional
    public Customer register(RegisterForm f) {
        if (!f.getPassword().equals(f.getConfirmPassword())) {
            throw new BusinessException("Password and confirmation do not match.");
        }
        String mobile = Validators.mobile(f.getMobile());
        if (customers.existsByMobile(mobile)) {
            throw new BusinessException("A customer profile with this mobile number already exists. Please call "
                    + props.getPhone() + " and we will activate your online account.");
        }
        AppUser u = users.create(f.getFullName(), f.getEmail(), mobile, Role.CUSTOMER, f.getPassword(), false);
        Customer c = new Customer();
        c.setName(f.getFullName().trim());
        c.setMobile(mobile);
        c.setEmail(f.getEmail().trim().toLowerCase());
        c.setAddress(Validators.blankToNull(f.getAddress()));
        c.setUser(u);
        c = customers.save(c);
        c.setCode(String.format("CUS-%05d", c.getId()));
        audit.log("CUSTOMER_REGISTERED", c.getCode() + " " + c.getName());
        return c;
    }

    @Transactional
    public Customer create(CustomerForm f) {
        String mobile = Validators.mobile(f.getMobile());
        if (customers.existsByMobile(mobile)) {
            throw new BusinessException("A customer with this mobile number already exists.");
        }
        Customer c = new Customer();
        apply(c, f, mobile);
        c = customers.save(c);
        c.setCode(String.format("CUS-%05d", c.getId()));
        audit.log("CUSTOMER_CREATED", c.getCode() + " " + c.getName());
        return c;
    }

    @Transactional
    public Customer update(Long id, CustomerForm f) {
        Customer c = get(id);
        String mobile = Validators.mobile(f.getMobile());
        customers.findByMobile(mobile).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BusinessException("Another customer already uses this mobile number.");
            }
        });
        apply(c, f, mobile);
        if (c.getUser() != null && f.getEmail() != null && !f.getEmail().equalsIgnoreCase(c.getUser().getEmail())) {
            // login email is changed only through the user admin screen; keep profile email in sync only if unused
            c.setEmail(c.getUser().getEmail());
        }
        audit.log("CUSTOMER_UPDATED", c.getCode() + " " + c.getName());
        return c;
    }

    private void apply(Customer c, CustomerForm f, String mobile) {
        c.setName(f.getName().trim());
        c.setMobile(mobile);
        c.setEmail(f.getEmail() == null ? null : f.getEmail().trim().toLowerCase());
        c.setAddress(Validators.blankToNull(f.getAddress()));
        c.setCity(Validators.blankToNull(f.getCity()));
        c.setPincode(Validators.blankToNull(f.getPincode()));
        c.setGstin(f.getGstin() == null ? null : f.getGstin().trim().toUpperCase());
        c.setNotes(Validators.blankToNull(f.getNotes()));
    }

    /** Creates a portal login for a walk-in customer. Returns the temporary password (shown once). */
    @Transactional
    public String enablePortal(Long customerId) {
        Customer c = get(customerId);
        if (c.getUser() != null) {
            throw new BusinessException("This customer already has a portal login.");
        }
        if (c.getEmail() == null || c.getEmail().isBlank()) {
            throw new BusinessException("Add the customer's email address first - it is used as the login.");
        }
        String temp = users.generatePassword();
        AppUser u = users.create(c.getName(), c.getEmail(), c.getMobile(), Role.CUSTOMER, temp, true);
        c.setUser(u);
        audit.log("PORTAL_ENABLED", c.getCode() + " " + c.getEmail());
        return temp;
    }
}
