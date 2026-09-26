package com.citycarservices.garage.config;

import com.citycarservices.garage.domain.*;
import com.citycarservices.garage.form.*;
import com.citycarservices.garage.repo.*;
import com.citycarservices.garage.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * First-run data: the service catalogue, the Super Admin account and (only when garage.seed-demo=true)
 * demo staff, customer, parts and a sample booking. Nothing is inserted twice.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_STAFF_PASSWORD = "Garage@123";
    private static final String DEMO_ADMIN_PASSWORD = "Admin@12345";
    private static final String DEMO_CUSTOMER_PASSWORD = "Customer@123";

    private final GarageProperties props;
    private final PlatformTransactionManager txManager;
    private final ServiceItemRepository serviceRepo;
    private final UserRepository userRepo;
    private final CustomerRepository customerRepo;
    private final MechanicRepository mechanicRepo;
    private final UserService users;
    private final MechanicService mechanics;
    private final VehicleService vehicles;
    private final InventoryService inventory;
    private final PickupService pickups;
    private final BookingService bookings;

    public DataSeeder(GarageProperties props, PlatformTransactionManager txManager, ServiceItemRepository serviceRepo,
                      UserRepository userRepo, CustomerRepository customerRepo, MechanicRepository mechanicRepo,
                      UserService users, MechanicService mechanics, VehicleService vehicles,
                      InventoryService inventory, PickupService pickups, BookingService bookings) {
        this.props = props;
        this.txManager = txManager;
        this.serviceRepo = serviceRepo;
        this.userRepo = userRepo;
        this.customerRepo = customerRepo;
        this.mechanicRepo = mechanicRepo;
        this.users = users;
        this.mechanics = mechanics;
        this.vehicles = vehicles;
        this.inventory = inventory;
        this.pickups = pickups;
        this.bookings = bookings;
    }

    @Override
    public void run(ApplicationArguments args) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> seedServices());
        tx.executeWithoutResult(s -> seedAdmin());
        if (props.isSeedDemo()) {
            tx.executeWithoutResult(s -> seedDemo());
        }
    }

    // ------------------------------------------------------------------ catalogue

    private void svc(int order, String name, ServiceCategory cat, String icon, String price, String desc,
                     String hours, Integer km, Integer months) {
        ServiceItem s = new ServiceItem();
        s.setName(name);
        s.setCategory(cat);
        s.setIcon(icon);
        s.setBasePrice(new BigDecimal(price));
        s.setDescription(desc);
        s.setEstimatedHours(hours == null ? null : new BigDecimal(hours));
        s.setIntervalKm(km);
        s.setIntervalMonths(months);
        s.setGstRate(new BigDecimal("18.00"));
        s.setSacCode("9987");
        s.setSortOrder(order);
        serviceRepo.save(s);
    }

    private void seedServices() {
        if (serviceRepo.count() > 0) {
            return;
        }
        // Prices are indicative "starting from" amounts - edit them under Admin > Services.
        svc(1, "General / Periodic Service", ServiceCategory.SERVICING, "🔧", "2499",
                "Complete periodic service: oil, filters, fluids top-up, 40-point check and road test.", "3.0", 10000, 6);
        svc(2, "Engine Oil & Filter Replacement", ServiceCategory.SERVICING, "🛢️", "1499",
                "Engine oil and oil filter change with the grade recommended for your car.", "1.0", 10000, 6);
        svc(3, "Engine Inspection & Repair", ServiceCategory.ENGINE, "⚙️", "999",
                "Diagnosis of noise, smoke, misfire, overheating and power loss, followed by repair.", "2.0", null, null);
        svc(4, "Brake Service", ServiceCategory.BRAKES_CLUTCH, "🛑", "1199",
                "Brake pad / shoe inspection and replacement, disc skimming, brake fluid and bleeding.", "1.5", null, null);
        svc(5, "Clutch Repair / Replacement", ServiceCategory.BRAKES_CLUTCH, "🦶", "3999",
                "Clutch plate, pressure plate and release bearing repair or replacement.", "5.0", null, null);
        svc(6, "Suspension Repair", ServiceCategory.SUSPENSION, "🚗", "1999",
                "Shock absorbers, bushes, links, ball joints and steering rack checks and repairs.", "3.0", null, null);
        svc(7, "Battery Inspection & Replacement", ServiceCategory.ELECTRICAL_AC, "🔋", "299",
                "Battery health test, terminal cleaning, charging system check and replacement.", "0.5", null, null);
        svc(8, "AC Diagnosis & Repair", ServiceCategory.ELECTRICAL_AC, "❄️", "899",
                "Cooling check, gas top-up, compressor, condenser, blower and cabin filter service.", "2.0", null, null);
        svc(9, "Electrical & Electronic Diagnosis", ServiceCategory.ELECTRICAL_AC, "💡", "799",
                "Scanner diagnosis, wiring, sensors, lights, power windows, central locking and warning lamps.", "1.5", null, null);
        svc(10, "Wheel Alignment", ServiceCategory.TYRES_WHEELS, "🎯", "699",
                "Computerised 3D wheel alignment for even tyre wear and straight tracking.", "1.0", null, null);
        svc(11, "Wheel Balancing", ServiceCategory.TYRES_WHEELS, "⚖️", "499",
                "Computerised balancing to remove steering vibration at speed.", "0.75", null, null);
        svc(12, "Tyre Services", ServiceCategory.TYRES_WHEELS, "🛞", "299",
                "Puncture repair, tyre rotation, fitment and replacement.", "1.0", null, null);
        svc(13, "Denting & Painting", ServiceCategory.BODY_PAINT, "🎨", "2999",
                "Panel beating and computerised colour-matched painting, priced per panel.", "8.0", null, null);
        svc(14, "Body Repair", ServiceCategory.BODY_PAINT, "🛠️", "1999",
                "Bumper, door, fender and accident-damage repairs.", "6.0", null, null);
        svc(15, "Car Washing", ServiceCategory.CLEANING, "💧", "399",
                "Foam wash, vacuum and tyre dressing.", "0.75", null, null);
        svc(16, "Interior / Exterior Cleaning", ServiceCategory.CLEANING, "✨", "1299",
                "Deep interior shampoo, dashboard care and exterior polish.", "3.0", null, null);
        svc(17, "Detailing", ServiceCategory.CLEANING, "💎", "4999",
                "Paint correction, ceramic / wax coating, engine bay and interior detailing.", "8.0", null, null);
        svc(18, "Pickup & Drop", ServiceCategory.ASSISTANCE, "🚚", "199",
                "We collect your car from home or office and bring it back after service.", "0.0", null, null);
        svc(19, "Emergency / Breakdown Assistance", ServiceCategory.ASSISTANCE, "🆘", "799",
                "On-road help for battery, tyre, towing and starting problems. Call us for urgent help.", "1.0", null, null);
        svc(20, "Other / Custom Repairs", ServiceCategory.OTHER, "🧰", "0",
                "Anything else your car needs - describe the problem and we will inspect and quote.", null, null, null);
        log.info("Seeded {} services", serviceRepo.count());
    }

    // ------------------------------------------------------------------ admin

    private void seedAdmin() {
        boolean hasSuper = userRepo.findAll().stream().anyMatch(u -> u.getRole() == Role.SUPER_ADMIN);
        if (hasSuper) {
            return;
        }
        String email = props.getAdminEmail();
        String pw = props.getAdminPassword();
        boolean generated = false;
        if (pw == null || pw.isBlank()) {
            if (props.isSeedDemo()) {
                pw = DEMO_ADMIN_PASSWORD;
            } else {
                pw = users.generatePassword();
                generated = true;
            }
        }
        users.create("Super Admin", email, props.getPhone(), Role.SUPER_ADMIN, pw, generated);
        if (generated) {
            log.warn("=================================================================");
            log.warn(" Super Admin created: {}  temporary password: {}", email, pw);
            log.warn(" Change it at first login. Set ADMIN_PASSWORD to choose your own.");
            log.warn("=================================================================");
        } else if (props.isSeedDemo()) {
            log.warn("DEMO MODE: Super Admin {} / {} - set SEED_DEMO=false and ADMIN_PASSWORD for production.", email, pw);
        }
    }

    // ------------------------------------------------------------------ demo data

    private void seedDemo() {
        if (mechanicRepo.count() > 0 || customerRepo.count() > 0) {
            return;
        }
        users.create("Anil Kulkarni", "manager@citycarservices.in", "9800000001", Role.GARAGE_MANAGER, DEMO_STAFF_PASSWORD, false);
        users.create("Sneha Joshi", "advisor@citycarservices.in", "9800000002", Role.SERVICE_ADVISOR, DEMO_STAFF_PASSWORD, false);
        users.create("Farhan Shaikh", "accounts@citycarservices.in", "9800000003", Role.ACCOUNTANT, DEMO_STAFF_PASSWORD, false);
        users.create("Prakash More", "inventory@citycarservices.in", "9800000004", Role.INVENTORY_MANAGER, DEMO_STAFF_PASSWORD, false);

        demoMechanic("Ramesh Patil", "ramesh@citycarservices.in", "Engine, Transmission, Diagnostics", "Engine & Clutch", 12);
        demoMechanic("Sunil Gaikwad", "sunil@citycarservices.in", "Brakes, Suspension, Alignment", "Suspension & Brakes", 8);
        demoMechanic("Imran Khan", "imran@citycarservices.in", "AC, Electrical, Electronics", "AC & Electrical", 6);

        SupplierForm sf = new SupplierForm();
        sf.setName("Pune Auto Spares Pvt Ltd");
        sf.setContactPerson("Mahesh");
        sf.setPhone("9800001111");
        inventory.saveSupplier(null, sf);
        Supplier supplier = inventory.suppliers().get(0);

        part(supplier, "OIL-5W30-1L", "Engine Oil 5W-30 (1 litre)", "Castrol", "Lubricants", "550", "720", "28", "40", "10", "Ltr");
        part(supplier, "FLT-OIL-001", "Oil Filter", "Bosch", "Filters", "120", "220", "28", "15", "6", "Nos");
        part(supplier, "FLT-AIR-001", "Air Filter", "Mann", "Filters", "180", "340", "28", "10", "4", "Nos");
        part(supplier, "FLT-CAB-001", "Cabin / AC Filter", "Bosch", "Filters", "150", "320", "28", "8", "4", "Nos");
        part(supplier, "BRK-PAD-F01", "Front Brake Pad Set", "TVS Girling", "Brakes", "900", "1450", "28", "8", "3", "Set");
        part(supplier, "BRK-FLD-500", "Brake Fluid DOT-4 (500 ml)", "Bosch", "Lubricants", "180", "300", "28", "12", "4", "Nos");
        part(supplier, "BAT-35AH", "Car Battery 35Ah", "Exide", "Electrical", "3600", "4700", "28", "5", "2", "Nos");
        part(supplier, "SPK-PLUG-01", "Spark Plug", "NGK", "Ignition", "180", "320", "28", "24", "8", "Nos");
        part(supplier, "CLT-KIT-001", "Clutch Plate Kit", "Valeo", "Transmission", "3200", "4600", "28", "3", "2", "Set");
        part(supplier, "WPR-BLD-01", "Wiper Blade (pair)", "Bosch", "Body", "260", "480", "28", "3", "4", "Set");
        part(supplier, "CLNT-1L", "Coolant (1 litre)", "Shell", "Lubricants", "220", "380", "28", "2", "6", "Ltr");

        DriverForm d1 = new DriverForm();
        d1.setName("Vijay Shinde");
        d1.setPhone("9800002222");
        d1.setVehicleNo("MH12XY0001");
        pickups.saveDriver(d1);

        // Demo customer with a portal login
        AppUser cu = users.create("Rahul Deshmukh", "rahul@example.com", "9822012345", Role.CUSTOMER, DEMO_CUSTOMER_PASSWORD, false);
        Customer c = new Customer();
        c.setName("Rahul Deshmukh");
        c.setMobile("9822012345");
        c.setEmail("rahul@example.com");
        c.setAddress("Flat 12, Baner Road, Pune 411045");
        c.setCity("Pune");
        c.setUser(cu);
        c = customerRepo.save(c);
        c.setCode(String.format("CUS-%05d", c.getId()));

        VehicleForm vf = new VehicleForm();
        vf.setRegNo("MH12AB1234");
        vf.setMake("Maruti Suzuki");
        vf.setModel("Swift");
        vf.setVariant("VXi");
        vf.setMfgYear(2019);
        vf.setFuelType(FuelType.PETROL);
        vf.setOdometer(48200);
        vf.setVin("MA3FJEB1S00123456");
        vf.setInsurer("Sample General Insurance");
        vf.setPolicyNo("POL-DEMO-001");
        vf.setInsuranceExpiry(LocalDate.now().plusDays(20));
        vf.setPucExpiry(LocalDate.now().plusDays(12));
        vf.setNextServiceDate(LocalDate.now().plusDays(15));
        vf.setNextServiceKm(50000);
        Vehicle v = vehicles.create(c.getId(), vf);

        VehicleForm vf2 = new VehicleForm();
        vf2.setRegNo("MH14CD5678");
        vf2.setMake("Hyundai");
        vf2.setModel("Creta");
        vf2.setVariant("SX Diesel");
        vf2.setMfgYear(2021);
        vf2.setFuelType(FuelType.DIESEL);
        vf2.setOdometer(31000);
        vehicles.create(c.getId(), vf2);

        BookingForm bf = new BookingForm();
        bf.setVehicleId(v.getId());
        bf.setServiceIds(List.of(serviceRepo.findByName("General / Periodic Service").get().getId(),
                serviceRepo.findByName("Wheel Alignment").get().getId()));
        LocalDateTime t = LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0);
        bf.setPreferredAt(t);
        bf.setComplaint("Steering pulls to the left; service due.");
        bookings.create(bf, c, BookingSource.ONLINE, false);
        log.warn("DEMO DATA loaded. Staff password: {} | customer rahul@example.com / {}", DEMO_STAFF_PASSWORD,
                DEMO_CUSTOMER_PASSWORD);
    }

    private void demoMechanic(String name, String email, String skills, String spec, int years) {
        MechanicForm f = new MechanicForm();
        f.setFullName(name);
        f.setEmail(email);
        f.setPassword(DEMO_STAFF_PASSWORD);
        f.setSkills(skills);
        f.setSpecialization(spec);
        f.setExperienceYears(years);
        mechanics.create(f);
    }

    private void part(Supplier s, String no, String name, String brand, String cat, String buy, String sell,
                      String gst, String stock, String min, String unit) {
        PartForm f = new PartForm();
        f.setPartNo(no);
        f.setName(name);
        f.setBrand(brand);
        f.setCategory(cat);
        f.setPurchasePrice(new BigDecimal(buy));
        f.setSellingPrice(new BigDecimal(sell));
        f.setGstRate(new BigDecimal(gst));
        f.setOpeningStock(new BigDecimal(stock));
        f.setMinStock(new BigDecimal(min));
        f.setUnit(unit);
        f.setSupplierId(s.getId());
        inventory.create(f);
    }
}
