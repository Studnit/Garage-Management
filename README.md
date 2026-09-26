# City Car Services - Garage Management System

Spring Boot 3 + Thymeleaf application for **City Car Services**, a multi-brand car repair and service centre
(Shop No. 1, Radha Chowk, near Orchid Hotel, Mumbai Bypass Road, Baner, Pune 411045 - +91 86982 28740 - open daily 9 AM to 9 PM).

It implements the real workflow, not just screens:

`Customer -> Vehicle -> Booking -> Inspection -> Job card -> Estimate -> Approval -> Parts/Labour -> Invoice -> Payment -> Delivery -> Service history`

## Run it

Requires Java 21 and Maven 3.9+ (internet access on first build).

```bash
./run.sh            # Windows: run.bat
# or
mvn spring-boot:run
```

Open <http://localhost:8080>. The default profile uses an embedded **H2 file database** (`./data/garagedb`), so every
customer, vehicle, booking, job card and invoice is stored permanently and survives restarts.

Run the tests (boots the whole app on an in-memory DB and renders every screen for staff and customer):

```bash
mvn test
```

### Demo accounts (only when `garage.seed-demo=true`, the default outside `prod`)

| Role | Login | Password |
|---|---|---|
| Super Admin | admin@citycarservices.in | Admin@12345 |
| Garage Manager | manager@citycarservices.in | Garage@123 |
| Service Advisor | advisor@citycarservices.in | Garage@123 |
| Accountant | accounts@citycarservices.in | Garage@123 |
| Inventory Manager | inventory@citycarservices.in | Garage@123 |
| Customer | rahul@example.com | Customer@123 |

Customers can also self-register at `/register`. Staff can create a portal login for any walk-in customer from the customer page.

## Production

Use PostgreSQL (or MySQL) and the `prod` profile. Demo data is off and the admin account comes from the environment.

```bash
cp .env.example .env      # set DB_PASSWORD, ADMIN_EMAIL, ADMIN_PASSWORD
docker compose up -d --build
```

Without Docker:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/citycars   # or jdbc:mysql://localhost:3306/citycars
export DB_USERNAME=citycars DB_PASSWORD=...
export ADMIN_EMAIL=owner@citycarservices.in ADMIN_PASSWORD=...
java -jar target/city-car-services.jar
```

Serve it behind HTTPS (nginx / Caddy) and keep `COOKIE_SECURE=true`. If `ADMIN_PASSWORD` is empty the first start prints a generated one-time password in the log.

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Super Admin created on first start |
| `GARAGE_GSTIN`, `GARAGE_PAN` | Printed on invoices (blank until you set them) |
| `GARAGE_UPI_ID`, `GARAGE_BANK_DETAILS` | Shown to customers for online payment |
| `GARAGE_EMAIL`, `MAIL_FROM`, `spring.mail.*` | Outgoing email (optional) |
| `NOTIFY_WEBHOOK_URL` | POST target for WhatsApp/SMS gateway (optional) |
| `UPLOAD_DIR` | Inspection photos/videos (keep on a persistent volume) |
| `COOKIE_SECURE` | `true` when served over HTTPS |
| `DDL_AUTO` | Hibernate schema mode, default `update` |

Back up the database and `UPLOAD_DIR` regularly. Health check: `/actuator/health`.

## Modules

| Module | Where |
|---|---|
| Customers, vehicles | `/admin/customers`, `/admin/vehicles` |
| Service booking (Pending -> Confirmed -> Vehicle Received -> Inspection -> In Service -> Quality Check -> Ready -> Delivered -> Completed) | `/admin/bookings` |
| Garage services (20 seeded, editable) | `/admin/services`, public `/services` |
| Digital inspection (13 areas, photos/videos, defects, recommendations) | `/admin/jobs/{id}/inspection` |
| Job cards / work orders (printable) | `/admin/jobs` |
| Estimates with per-line approve/reject and approval history | job card, and customer portal |
| Mechanics and workload | `/admin/mechanics` |
| Spare parts, stock movements, suppliers, low-stock alerts | `/admin/inventory`, `/admin/suppliers` |
| GST invoices (CGST + SGST), payments, advance, balance, PDF and print | `/admin/invoices`, `/admin/payments` |
| Pickup and drop, drivers | `/admin/pickups` |
| Service, insurance and PUC reminders (daily 9 AM) | `/admin/reminders` |
| Dashboard and 11 reports with CSV export | `/admin`, `/admin/reports` |
| Customer portal | `/portal` |
| Users, roles, audit log, feedback | `/admin/users`, `/admin/audit`, `/admin/feedback` |

### Roles

Super Admin and Garage Manager (everything, users/audit are Super Admin only), Service Advisor (customers, bookings, estimates),
Mechanic (own jobs, inspections), Accountant (invoices, payments, reports), Inventory Manager (parts, suppliers), Customer (own data only).

## Business rules to know

* Invoice is generated automatically when the vehicle reaches **Ready**. GST is intra-state (CGST + SGST); discounts are apportioned per line and the total is rounded to the nearest rupee.
* Only customer-approved lines are billed. Approved parts are issued from stock.
* A vehicle is delivered only when the balance is zero (managers can override). Completion updates the vehicle's odometer and next service date/km.
* Payments submitted online by a customer stay **pending** until an accountant verifies them.
* Passwords are BCrypt-hashed, 5 wrong logins lock an account for 15 minutes, temporary passwords must be changed at first sign in, all forms are CSRF-protected.
* WhatsApp/SMS reminders send through `NOTIFY_WEBHOOK_URL` when configured, otherwise the reminders page offers one-click WhatsApp links.
* Service prices in the catalogue are editable starting prices, not official rates. GSTIN, PAN, UPI and bank details are intentionally blank until you enter them.
