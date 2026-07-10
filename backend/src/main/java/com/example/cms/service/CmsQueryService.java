package com.example.cms.service;

import com.example.cms.dto.BranchRequest;
import com.example.cms.dto.ChargeListRequest;
import com.example.cms.dto.CustomerRequest;
import com.example.cms.dto.CustomerWithContractRequest;
import com.example.cms.dto.ContractRequest;
import com.example.cms.dto.LoginRequest;
import com.example.cms.dto.OfficeContactRequest;
import com.example.cms.dto.OfficeRequest;
import com.example.cms.dto.RegisterRequest;
import com.example.cms.dto.RentPaymentRequest;
import com.example.cms.dto.StaffUpdateRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CmsQueryService {
    private final JdbcTemplate jdbc;

    public CmsQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customers", count("customers"));
        result.put("activeContracts", countWhere("contracts", "lease_status IN ('綁約中', '蝬?銝?')"));
        result.put("rentPayments", count("rent_payments"));
        result.put("refunds", count("refunds"));
        result.put("monthlyRentAmount", jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM rent_payments", Number.class));
        result.put("latestPayments", jdbc.queryForList("""
                SELECT rp.rent_payment_id, c.company_name, rp.payment_month, rp.amount, rp.receipt_no
                FROM rent_payments rp
                JOIN customers c ON c.customer_id = rp.customer_id
                ORDER BY rp.rent_payment_id DESC
                LIMIT 8
                """));
        result.put("notifications", dashboardNotifications());
        return result;
    }

    private Map<String, Object> dashboardNotifications() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        Map<String, Object> notifications = new LinkedHashMap<>();
        notifications.put("expiringContracts", expiringContractNotifications(today));
        notifications.put("ownerBirthdays", ownerBirthdayNotifications(today));
        return notifications;
    }

    private List<Map<String, Object>> expiringContractNotifications(LocalDate today) {
        return jdbc.queryForList("""
                SELECT c.company_name, co.end_date_text, co.lease_status
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                WHERE co.lease_status IN ('綁約中', '蝬?銝?')
                  AND co.end_date_text IS NOT NULL
                  AND co.end_date_text <> ''
                ORDER BY co.end_date_text, c.company_name
                """).stream()
                .filter(row -> shouldShowExpirationReminder((String) row.get("end_date_text"), today))
                .limit(50)
                .toList();
    }

    private List<Map<String, Object>> ownerBirthdayNotifications(LocalDate today) {
        return jdbc.queryForList("""
                SELECT company_name, owner_name, owner_birthday
                FROM customers
                WHERE owner_birthday IS NOT NULL
                  AND owner_birthday <> ''
                ORDER BY owner_birthday, company_name
                """).stream()
                .filter(row -> isBirthdayInMonth((String) row.get("owner_birthday"), today))
                .limit(50)
                .toList();
    }

    public Map<String, Object> login(LoginRequest request) {
        if (request.account() == null || request.account().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account and password are required");
        }
        try {
            Map<String, Object> user = jdbc.queryForMap("""
                    SELECT s.staff_id, s.staff_name, s.account, s.password_hash, s.branch_id,
                           b.branch_name, r.role_permission_id, r.role_name, r.scope
                    FROM staff s
                    JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                    LEFT JOIN branches b ON b.branch_id = s.branch_id
                    WHERE s.account = ?
                    """, request.account().trim());
            if (!passwordMatches((String) user.get("password_hash"), request.password())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account or password");
            }
            user.remove("password_hash");
            applyPermissions(user);
            return user;
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account or password");
        }
    }

    public Map<String, Object> register(RegisterRequest request) {
        if (request.staffName() == null || request.staffName().isBlank()
                || request.account() == null || request.account().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.roleName() == null || request.roleName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "all fields are required");
        }
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE account = ?",
                Integer.class, request.account().trim());
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "account already exists");
        }
        Long roleId;
        try {
            roleId = jdbc.queryForObject("SELECT role_permission_id FROM role_permissions WHERE role_name = ?",
                    Long.class, request.roleName().trim());
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid role");
        }
        Long staffId = nextId("staff", "staff_id");
        jdbc.update("""
                INSERT INTO staff (staff_id, role_permission_id, branch_id, staff_name, account, password_hash)
                VALUES (?, ?, 1, ?, ?, ?)
                """, staffId, roleId, request.staffName().trim(), request.account().trim(),
                "{noop}" + request.password());
        Map<String, Object> user = jdbc.queryForMap("""
                SELECT s.staff_id, s.staff_name, s.account, s.branch_id,
                       b.branch_name, r.role_permission_id, r.role_name, r.scope
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                WHERE s.staff_id = ?
                """, staffId);
        applyPermissions(user);
        return user;
    }

    public List<Map<String, Object>> customers(String search, String ownerName, String companyName, String taxId,
                                               String phone, Long branchId, String officeNo) {
        if (hasCustomerFilters(companyName, taxId, phone, ownerName, branchId, officeNo)) {
            String companyLike = "%" + blankToEmpty(companyName) + "%";
            String taxIdLike = "%" + blankToEmpty(taxId) + "%";
            String phoneLike = "%" + blankToEmpty(phone) + "%";
            String ownerLike = "%" + blankToEmpty(ownerName) + "%";
            String officeLike = "%" + blankToEmpty(officeNo) + "%";
            return jdbc.queryForList(customerListSql() + """
                    WHERE c.customer_id IN (
                        SELECT DISTINCT filtered.customer_id
                        FROM customers filtered
                        LEFT JOIN contracts filtered_contract ON filtered_contract.customer_id = filtered.customer_id
                        LEFT JOIN offices filtered_office ON filtered_office.office_id = filtered_contract.office_id
                        WHERE (? = '%%' OR filtered.company_name LIKE ?)
                          AND (? = '%%' OR filtered.tax_id LIKE ?)
                          AND (? = '%%' OR filtered.phone LIKE ?)
                          AND (? = '%%' OR filtered.owner_name LIKE ?)
                          AND (? IS NULL OR filtered_office.branch_id = ?)
                          AND (? = '%%' OR filtered_office.office_no LIKE ?)
                    )
                    ORDER BY c.customer_id DESC
                    LIMIT 200
                    """, companyLike, companyLike,
                    taxIdLike, taxIdLike,
                    phoneLike, phoneLike,
                    ownerLike, ownerLike,
                    branchId, branchId,
                    officeLike, officeLike);
        }
        String searchText = search == null ? "" : search.trim();
        if (searchText.isBlank()) {
            return jdbc.queryForList(customerListSql() + """
                    ORDER BY c.customer_id DESC
                    LIMIT 200
                    """);
        }
        String like = "%" + searchText + "%";
        return jdbc.queryForList(customerListSql() + """
                WHERE c.company_name LIKE ?
                   OR c.owner_name LIKE ?
                   OR c.tax_id LIKE ?
                   OR c.owner_name IN (
                       SELECT DISTINCT matched.owner_name
                       FROM customers matched
                       WHERE matched.owner_name IS NOT NULL
                         AND matched.owner_name <> ''
                         AND (
                             matched.company_name LIKE ?
                             OR matched.owner_name LIKE ?
                             OR matched.tax_id LIKE ?
                         )
                   )
                ORDER BY c.owner_name, c.customer_id DESC
                LIMIT 200
                """, like, like, like, like, like, like);
    }

    public Map<String, Object> customerDetail(long id) {
        Map<String, Object> detail = jdbc.queryForMap(customerListSql() + " WHERE c.customer_id = ?", id);
        String ownerName = (String) detail.get("owner_name");
        detail.put("contracts", jdbc.queryForList("""
                SELECT co.*, o.office_no, b.branch_name, s.staff_name AS signer_staff_name
                FROM contracts co
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                LEFT JOIN staff s ON s.staff_id = co.signer_staff_id
                WHERE co.customer_id = ?
                ORDER BY co.contract_id DESC
                """, id));
        detail.put("sameOwnerCompanies", sameOwnerCompanies(ownerName, id));
        detail.put("rentPayments", jdbc.queryForList("""
                SELECT * FROM rent_payments
                WHERE customer_id = ?
                ORDER BY payment_month, rent_payment_id
                """, id));
        detail.put("chargeLists", jdbc.queryForList("""
                SELECT * FROM charge_lists
                WHERE customer_id = ?
                ORDER BY issued_at DESC
                """, id));
        detail.put("refunds", jdbc.queryForList("""
                SELECT * FROM refunds
                WHERE customer_id = ? OR company_name = ?
                ORDER BY refund_id DESC
                """, id, detail.get("company_name")));
        return detail;
    }

    public List<Map<String, Object>> customerLookup(String search) {
        String searchText = search == null ? "" : search.trim();
        String like = "%" + searchText + "%";
        return jdbc.queryForList("""
                SELECT c.customer_id, c.company_name, c.tax_id, c.status, c.owner_name,
                       c.rental_item, c.rental_status, c.owner_birthday,
                       c.contact_person, c.phone, c.registration_type, c.referrer,
                       co.contract_id, co.lease_status, co.rent, co.deposit,
                       o.office_no, b.branch_name
                FROM customers c
                LEFT JOIN contracts co ON co.customer_id = c.customer_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                WHERE (? = '%%'
                   OR c.company_name LIKE ?
                   OR c.owner_name LIKE ?
                   OR c.tax_id LIKE ?)
                ORDER BY c.company_name
                LIMIT 20
                """, like, like, like, like);
    }

    public Map<String, Object> createCustomer(CustomerRequest request) {
        Long id = nextId("customers", "customer_id");
        jdbc.update("""
                INSERT INTO customers (
                    customer_id, company_name, tax_id, status, rental_item, rental_status,
                    owner_name, owner_birthday, contact_person, phone, forwarding_address,
                    petty_cash, referrer, notes, registration_type, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                id,
                requiredCompanyName(request.companyName()),
                blankToNull(request.taxId()),
                normalizeStatus(request.status()),
                normalizeRentalItem(request.rentalItem(), request.registrationType()),
                normalizeRentalStatus(request.rentalStatus()),
                blankToNull(request.ownerName()),
                blankToNull(request.ownerBirthday()),
                blankToNull(request.contactPerson()),
                blankToNull(request.phone()),
                blankToNull(request.forwardingAddress()),
                request.pettyCash(),
                blankToNull(request.referrer()),
                blankToNull(request.notes()),
                normalizeRegistrationType(request.registrationType(), request.rentalItem()),
                request.updatedBy() == null ? 1L : request.updatedBy());
        return customerDetail(id);
    }

    @Transactional
    public Map<String, Object> createCustomerWithContract(CustomerWithContractRequest request, MultipartFile leaseImage) {
        if (request == null || request.customer() == null || request.contract() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customer and contract are required");
        }
        Map<String, Object> customer = createCustomer(request.customer());
        Long customerId = ((Number) customer.get("customer_id")).longValue();
        ContractRequest contract = request.contract();
        Map<String, Object> createdContract = createContract(new ContractRequest(
                customerId,
                contract.officeId(),
                contract.rentalItem(),
                contract.rentalStatus(),
                contract.signedDateText(),
                contract.signerStaffId(),
                contract.paymentMonths(),
                contract.startDateText(),
                contract.endDateText(),
                contract.terminationDateText(),
                contract.rent(),
                contract.deposit(),
                contract.leaseImagePath(),
                contract.leaseStatus(),
                contract.updatedBy() == null ? request.customer().updatedBy() : contract.updatedBy()));
        if (leaseImage != null && !leaseImage.isEmpty()) {
            Long contractId = ((Number) createdContract.get("contract_id")).longValue();
            String imagePath = saveLeaseImage(contractId, leaseImage);
            jdbc.update("UPDATE contracts SET lease_image_path = ? WHERE contract_id = ?", imagePath, contractId);
        }
        return customerDetail(customerId);
    }

    public Map<String, Object> updateCustomer(long id, CustomerRequest request) {
        jdbc.update("""
                UPDATE customers
                SET company_name = ?,
                    tax_id = ?,
                    status = ?,
                    rental_item = ?,
                    rental_status = ?,
                    owner_name = ?,
                    owner_birthday = ?,
                    contact_person = ?,
                    phone = ?,
                    forwarding_address = ?,
                    petty_cash = ?,
                    referrer = ?,
                    notes = ?,
                    registration_type = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE customer_id = ?
                """,
                requiredCompanyName(request.companyName()),
                blankToNull(request.taxId()),
                normalizeStatus(request.status()),
                normalizeRentalItem(request.rentalItem(), request.registrationType()),
                normalizeRentalStatus(request.rentalStatus()),
                blankToNull(request.ownerName()),
                blankToNull(request.ownerBirthday()),
                blankToNull(request.contactPerson()),
                blankToNull(request.phone()),
                blankToNull(request.forwardingAddress()),
                request.pettyCash(),
                blankToNull(request.referrer()),
                blankToNull(request.notes()),
                normalizeRegistrationType(request.registrationType(), request.rentalItem()),
                request.updatedBy() == null ? 1L : request.updatedBy(),
                id);
        return customerDetail(id);
    }

    public List<Map<String, Object>> sameOwnerCompanies(String ownerName, long excludeCustomerId) {
        if (ownerName == null || ownerName.isBlank()) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT c.customer_id, c.company_name, c.owner_name, c.phone, c.status,
                       c.rental_item, c.rental_status, c.registration_type
                FROM customers c
                WHERE c.owner_name = ? AND c.customer_id <> ?
                ORDER BY c.company_name
                """, ownerName, excludeCustomerId);
    }

    public List<Map<String, Object>> branches() {
        return jdbc.queryForList("SELECT * FROM branches ORDER BY branch_id");
    }

    public Map<String, Object> createBranch(BranchRequest request) {
        String name = requiredBranchName(request.branchName());
        Long id = nextId("branches", "branch_id");
        jdbc.update("INSERT INTO branches (branch_id, branch_name) VALUES (?, ?)", id, name);
        return jdbc.queryForMap("SELECT * FROM branches WHERE branch_id = ?", id);
    }

    public Map<String, Object> updateBranch(long id, BranchRequest request) {
        jdbc.update("UPDATE branches SET branch_name = ? WHERE branch_id = ?",
                requiredBranchName(request.branchName()), id);
        return jdbc.queryForMap("SELECT * FROM branches WHERE branch_id = ?", id);
    }

    public List<Map<String, Object>> offices(Long branchId) {
        List<Map<String, Object>> rows;
        if (branchId != null) {
            rows = jdbc.queryForList("""
                    SELECT o.*, b.branch_name
                    FROM offices o
                    JOIN branches b ON b.branch_id = o.branch_id
                    WHERE o.branch_id = ?
                    ORDER BY o.office_id
                    """, branchId);
        } else {
            rows = jdbc.queryForList("""
                    SELECT o.*, b.branch_name
                    FROM offices o
                    JOIN branches b ON b.branch_id = o.branch_id
                    ORDER BY o.office_id
                    """);
        }
        rows.forEach(row -> {
            Long officeId = ((Number) row.get("office_id")).longValue();
            row.put("contacts", jdbc.queryForList("""
                    SELECT office_contact_id, person_name, phone
                    FROM office_contacts
                    WHERE office_id = ?
                    ORDER BY office_contact_id
                    """, officeId));
        });
        return rows;
    }

    public Map<String, Object> createOffice(OfficeRequest request) {
        requiredId(request.branchId(), "branchId");
        Long id = nextId("offices", "office_id");
        jdbc.update("""
                INSERT INTO offices (office_id, office_no, branch_id, notes)
                VALUES (?, ?, ?, ?)
                """,
                id,
                blankToNull(request.officeNo()),
                request.branchId(),
                blankToNull(request.notes()));
        saveOfficeContacts(id, request.contacts());
        return officeDetail(id);
    }

    public Map<String, Object> updateOffice(long id, OfficeRequest request) {
        requiredId(request.branchId(), "branchId");
        jdbc.update("""
                UPDATE offices
                SET office_no = ?,
                    branch_id = ?,
                    notes = ?
                WHERE office_id = ?
                """,
                blankToNull(request.officeNo()),
                request.branchId(),
                blankToNull(request.notes()),
                id);
        saveOfficeContacts(id, request.contacts());
        return officeDetail(id);
    }

    private Map<String, Object> officeDetail(long id) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT o.*, b.branch_name
                FROM offices o
                JOIN branches b ON b.branch_id = o.branch_id
                WHERE o.office_id = ?
                """, id);
        row.put("contacts", jdbc.queryForList("""
                SELECT office_contact_id, person_name, phone
                FROM office_contacts
                WHERE office_id = ?
                ORDER BY office_contact_id
                """, id));
        return row;
    }

    private void saveOfficeContacts(long officeId, java.util.List<OfficeContactRequest> contacts) {
        jdbc.update("DELETE FROM office_contacts WHERE office_id = ?", officeId);
        if (contacts == null || contacts.isEmpty()) {
            return;
        }
        Long baseId = nextId("office_contacts", "office_contact_id");
        for (int i = 0; i < contacts.size(); i++) {
            OfficeContactRequest c = contacts.get(i);
            jdbc.update("""
                    INSERT INTO office_contacts (office_contact_id, office_id, person_name, phone)
                    VALUES (?, ?, ?, ?)
                    """,
                    baseId + i,
                    officeId,
                    blankToNull(c.personName()),
                    blankToNull(c.phone()));
        }
    }

    public List<Map<String, Object>> contracts(String search, String companyName, String startDateText, String endDateText, String leaseStatus) {
        String searchText = search == null ? "" : search.trim();
        String like = "%" + searchText + "%";
        String companyLike = "%" + (companyName == null ? "" : companyName.trim()) + "%";
        String startText = startDateText == null ? "" : startDateText.trim();
        String endText = endDateText == null ? "" : endDateText.trim();
        String statusText = leaseStatus == null ? "" : leaseStatus.trim();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT co.*, c.company_name, c.owner_name, c.tax_id, o.office_no, b.branch_name,
                       s.staff_name AS signer_staff_name
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                LEFT JOIN staff s ON s.staff_id = co.signer_staff_id
                WHERE (? = '%%'
                   OR c.company_name LIKE ?
                   OR c.owner_name LIKE ?
                   OR c.tax_id LIKE ?
                   OR o.office_no LIKE ?
                   OR co.lease_status LIKE ?)
                  AND (? = '%%' OR c.company_name LIKE ?)
                  AND (? = '' OR co.lease_status = ?)
                ORDER BY co.contract_id DESC
                LIMIT 1000
                """, like, like, like, like, like, like,
                companyLike, companyLike,
                statusText, statusText);
        return rows.stream()
                .filter(row -> contractOverlaps(row, startText, endText))
                .limit(200)
                .toList();
    }

    public Map<String, Object> createContract(ContractRequest request) {
        Long id = nextId("contracts", "contract_id");
        jdbc.update("""
                INSERT INTO contracts (
                    contract_id, customer_id, office_id, rental_item, rental_status, signed_date_text,
                    signer_staff_id, payment_months, start_date_text, end_date_text, termination_date_text,
                    rent, deposit, lease_image_path, lease_status, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                id,
                requiredId(request.customerId(), "customerId"),
                request.officeId(),
                normalizeContractRentalItem(request.rentalItem()),
                normalizeContractRentalStatus(request.rentalStatus()),
                blankToNull(request.signedDateText()),
                request.signerStaffId(),
                request.paymentMonths(),
                blankToNull(request.startDateText()),
                blankToNull(request.endDateText()),
                blankToNull(request.terminationDateText()),
                request.rent(),
                request.deposit(),
                blankToNull(request.leaseImagePath()),
                normalizeContractStatus(request.leaseStatus()),
                request.updatedBy() == null ? 1L : request.updatedBy());
        return contractDetail(id);
    }

    public Map<String, Object> updateContract(long id, ContractRequest request) {
        jdbc.update("""
                UPDATE contracts
                SET lease_status = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE contract_id = ?
                """,
                normalizeContractStatus(request.leaseStatus()),
                request.updatedBy() == null ? 1L : request.updatedBy(),
                id);
        return contractDetail(id);
    }

    public List<Map<String, Object>> staff(Long branchId) {
        if (branchId != null) {
            return jdbc.queryForList(staffListSql() + " WHERE s.branch_id = ? ORDER BY s.staff_id", branchId);
        }
        return jdbc.queryForList(staffListSql() + " ORDER BY s.staff_id");
    }

    public Map<String, Object> updateStaff(long id, StaffUpdateRequest request) {
        if (request.rolePermissionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rolePermissionId is required");
        }
        jdbc.update("""
                UPDATE staff
                SET role_permission_id = ?
                WHERE staff_id = ?
                """, request.rolePermissionId(), id);
        return jdbc.queryForMap(staffListSql() + " WHERE s.staff_id = ?", id);
    }

    private Map<String, Object> contractDetail(long id) {
        return jdbc.queryForMap("""
                SELECT co.*, c.company_name, c.owner_name, c.tax_id, o.office_no, b.branch_name,
                       s.staff_name AS signer_staff_name
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                LEFT JOIN staff s ON s.staff_id = co.signer_staff_id
                WHERE co.contract_id = ?
                """, id);
    }

    public List<Map<String, Object>> rentPayments(String search) {
        String searchText = search == null ? "" : search.trim();
        String like = "%" + searchText + "%";
        return jdbc.queryForList("""
                SELECT rp.*, c.company_name, c.owner_name, c.tax_id
                FROM rent_payments rp
                JOIN customers c ON c.customer_id = rp.customer_id
                WHERE (? = '%%'
                   OR c.company_name LIKE ?
                   OR c.owner_name LIKE ?
                   OR c.tax_id LIKE ?
                   OR rp.receipt_no LIKE ?)
                ORDER BY rp.rent_payment_id DESC
                LIMIT 300
                """, like, like, like, like, like);
    }

    public Map<String, Object> createRentPayment(RentPaymentRequest request) {
        Long id = nextId("rent_payments", "rent_payment_id");
        jdbc.update("""
                INSERT INTO rent_payments (
                    rent_payment_id, customer_id, contract_id, payment_month, payment_date_text,
                    fee_start_date_text, fee_end_date_text, amount, receipt_no, note, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                request.customerId(),
                request.contractId(),
                request.paymentMonth(),
                request.paymentDateText(),
                request.feeStartDateText(),
                request.feeEndDateText(),
                request.amount(),
                request.receiptNo(),
                request.note(),
                request.updatedBy() == null ? 1L : request.updatedBy());
        return jdbc.queryForMap("SELECT * FROM rent_payments WHERE rent_payment_id = ?", id);
    }

    public Map<String, Object> updateRentPayment(long id, RentPaymentRequest request) {
        jdbc.update("""
                UPDATE rent_payments
                SET payment_month = ?,
                    payment_date_text = ?,
                    fee_start_date_text = ?,
                    fee_end_date_text = ?,
                    amount = ?,
                    receipt_no = ?,
                    note = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE rent_payment_id = ?
                """,
                request.paymentMonth(),
                request.paymentDateText(),
                request.feeStartDateText(),
                request.feeEndDateText(),
                request.amount(),
                request.receiptNo(),
                request.note(),
                request.updatedBy() == null ? 1L : request.updatedBy(),
                id);
        return jdbc.queryForMap("""
                SELECT rp.*, c.company_name, c.owner_name, c.tax_id
                FROM rent_payments rp
                JOIN customers c ON c.customer_id = rp.customer_id
                WHERE rp.rent_payment_id = ?
                """, id);
    }

    private static final Map<String, String> CHARGE_LIST_SORT_COLUMNS = Map.of(
            "chargeListId", "cl.charge_list_id",
            "customerId", "cl.customer_id",
            "feeStartMonth", "cl.fee_start_month",
            "feeEndMonth", "cl.fee_end_month",
            "totalAmount", "cl.total_amount",
            "status", "cl.status",
            "issuedAt", "cl.issued_at"
    );

    public Map<String, Object> chargeLists(Long chargeListId, Long customerId, Long contractId,
                                           String feeStartMonth, String feeEndMonth, Integer status,
                                           Long createdBy, String issuedFrom, String issuedTo,
                                           Integer page, Integer pageSize, String sortBy, String sortDir) {
        String feeStartMonthText = blankToEmpty(feeStartMonth);
        String feeEndMonthText = blankToEmpty(feeEndMonth);
        String issuedFromText = blankToEmpty(issuedFrom);
        String issuedToText = blankToEmpty(issuedTo);

        String where = """
                 WHERE (? IS NULL OR cl.charge_list_id = ?)
                   AND (? IS NULL OR cl.customer_id = ?)
                   AND (? IS NULL OR cl.contract_id = ?)
                   AND (? = '' OR cl.fee_start_month = ?)
                   AND (? = '' OR cl.fee_end_month = ?)
                   AND (? IS NULL OR cl.status = ?)
                   AND (? IS NULL OR cl.created_by = ?)
                   AND (? = '' OR DATE(cl.issued_at) >= ?)
                   AND (? = '' OR DATE(cl.issued_at) <= ?)
                """;
        List<Object> args = new ArrayList<>();
        args.add(chargeListId); args.add(chargeListId);
        args.add(customerId); args.add(customerId);
        args.add(contractId); args.add(contractId);
        args.add(feeStartMonthText); args.add(feeStartMonthText);
        args.add(feeEndMonthText); args.add(feeEndMonthText);
        args.add(status); args.add(status);
        args.add(createdBy); args.add(createdBy);
        args.add(issuedFromText); args.add(issuedFromText);
        args.add(issuedToText); args.add(issuedToText);

        String sortColumn = sortBy == null ? "cl.issued_at" : CHARGE_LIST_SORT_COLUMNS.getOrDefault(sortBy, "cl.issued_at");
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;

        List<Object> selectArgs = new ArrayList<>(args);
        selectArgs.add(size);
        selectArgs.add(pageNumber * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                chargeListListSql() + where + " ORDER BY " + sortColumn + " " + direction + " LIMIT ? OFFSET ?",
                selectArgs.toArray());

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM charge_lists cl" + where, Long.class, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    public Map<String, Object> chargeListDetail(long id) {
        try {
            return jdbc.queryForMap(chargeListListSql() + " WHERE cl.charge_list_id = ?", id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "charge list not found");
        }
    }

    public Map<String, Object> createChargeList(ChargeListRequest request) {
        requireExistingCustomer(request.customerId());
        requireExistingContract(request.contractId());
        requireContractBelongsToCustomer(request.contractId(), request.customerId());
        requireValidMonthRange(request.feeStartMonth(), request.feeEndMonth());
        requireNonNegativeAmounts(request);
        BigDecimal totalAmount = calculateTotalAmount(request);
        Long id = nextId("charge_lists", "charge_list_id");
        Long staffId = request.updatedBy() == null ? 1L : request.updatedBy();
        jdbc.update("""
                INSERT INTO charge_lists (
                    charge_list_id, customer_id, contract_id, fee_start_month, fee_end_month,
                    management_fee, electricity_fee, printing_fee, tax, advance_payment, repair_fee,
                    total_amount, status, created_by, issued_at, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 2, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                """,
                id, request.customerId(), request.contractId(), request.feeStartMonth(), request.feeEndMonth(),
                zeroIfNull(request.managementFee()), zeroIfNull(request.electricityFee()), zeroIfNull(request.printingFee()),
                zeroIfNull(request.tax()), zeroIfNull(request.advancePayment()), zeroIfNull(request.repairFee()),
                totalAmount, staffId, staffId);
        return chargeListDetail(id);
    }

    public Map<String, Object> updateChargeList(long id, ChargeListRequest request) {
        requireValidMonthRange(request.feeStartMonth(), request.feeEndMonth());
        requireNonNegativeAmounts(request);
        BigDecimal totalAmount = calculateTotalAmount(request);
        Long staffId = request.updatedBy() == null ? 1L : request.updatedBy();
        int updated = jdbc.update("""
                UPDATE charge_lists
                SET fee_start_month = ?,
                    fee_end_month = ?,
                    management_fee = ?,
                    electricity_fee = ?,
                    printing_fee = ?,
                    tax = ?,
                    advance_payment = ?,
                    repair_fee = ?,
                    total_amount = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE charge_list_id = ?
                """,
                request.feeStartMonth(), request.feeEndMonth(),
                zeroIfNull(request.managementFee()), zeroIfNull(request.electricityFee()), zeroIfNull(request.printingFee()),
                zeroIfNull(request.tax()), zeroIfNull(request.advancePayment()), zeroIfNull(request.repairFee()),
                totalAmount, staffId, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "charge list not found");
        }
        return chargeListDetail(id);
    }

    private String chargeListListSql() {
        return """
                SELECT cl.*, c.company_name, o.office_no, b.branch_name, co.rent AS contract_rent, s.staff_name AS created_by_name
                FROM charge_lists cl
                JOIN customers c ON c.customer_id = cl.customer_id
                LEFT JOIN contracts co ON co.contract_id = cl.contract_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                LEFT JOIN staff s ON s.staff_id = cl.created_by
                """;
    }

    private void requireExistingCustomer(Long customerId) {
        requiredId(customerId, "customerId");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE customer_id = ?", Integer.class, customerId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("customerId not found");
        }
    }

    private void requireExistingContract(Long contractId) {
        requiredId(contractId, "contractId");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE contract_id = ?", Integer.class, contractId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("contractId not found");
        }
    }

    private void requireContractBelongsToCustomer(Long contractId, Long customerId) {
        Long actualCustomerId = jdbc.queryForObject("SELECT customer_id FROM contracts WHERE contract_id = ?", Long.class, contractId);
        if (actualCustomerId == null || !actualCustomerId.equals(customerId)) {
            throw new IllegalArgumentException("contractId does not belong to customerId");
        }
    }

    private void requireValidMonthRange(String feeStartMonth, String feeEndMonth) {
        String start = requiredMonth(feeStartMonth, "feeStartMonth");
        String end = requiredMonth(feeEndMonth, "feeEndMonth");
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("feeStartMonth must not be after feeEndMonth");
        }
    }

    private String requiredMonth(String value, String fieldName) {
        if (value == null || !value.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException(fieldName + " must be in YYYY-MM format");
        }
        return value;
    }

    private void requireNonNegativeAmounts(ChargeListRequest request) {
        requireNonNegative(request.managementFee(), "managementFee");
        requireNonNegative(request.electricityFee(), "electricityFee");
        requireNonNegative(request.printingFee(), "printingFee");
        requireNonNegative(request.tax(), "tax");
        requireNonNegative(request.advancePayment(), "advancePayment");
        requireNonNegative(request.repairFee(), "repairFee");
    }

    private void requireNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private BigDecimal calculateTotalAmount(ChargeListRequest request) {
        return zeroIfNull(request.managementFee())
                .add(zeroIfNull(request.electricityFee()))
                .add(zeroIfNull(request.printingFee()))
                .add(zeroIfNull(request.tax()))
                .add(zeroIfNull(request.advancePayment()))
                .add(zeroIfNull(request.repairFee()));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public List<Map<String, Object>> refunds() {
        return jdbc.queryForList("""
                SELECT r.*, c.company_name AS matched_company_name
                FROM refunds r
                LEFT JOIN customers c ON c.customer_id = r.customer_id
                ORDER BY r.refund_id DESC
                LIMIT 200
                """);
    }

    public Map<String, Object> metadata() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("branches", jdbc.queryForList("SELECT * FROM branches ORDER BY branch_id"));
        result.put("owners", jdbc.queryForList("""
                SELECT owner_name, COUNT(*) AS customer_count
                FROM customers
                WHERE owner_name IS NOT NULL AND owner_name <> ''
                GROUP BY owner_name
                ORDER BY owner_name
                """));
        result.put("roles", jdbc.queryForList("SELECT * FROM role_permissions ORDER BY role_permission_id"));
        result.put("staff", jdbc.queryForList("""
                SELECT s.*, r.role_name, b.branch_name
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                ORDER BY s.staff_id
                """));
        result.put("salesTargets", jdbc.queryForList("""
                SELECT st.sales_target_id, st.branch_id, st.target_month AS `month`,
                       st.category, st.target_count, b.branch_name
                FROM sales_targets st JOIN branches b ON b.branch_id = st.branch_id
                ORDER BY st.target_month, st.category
                """));
        return result;
    }

    private String customerListSql() {
        return """
                SELECT c.*,
                       co.contract_id, co.lease_status, co.rent, co.deposit,
                       o.office_no, b.branch_name
                FROM customers c
                LEFT JOIN contracts co ON co.customer_id = c.customer_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                """;
    }

    private String staffListSql() {
        return """
                SELECT s.staff_id, s.staff_name, s.account, s.role_permission_id, s.branch_id,
                       r.role_name, b.branch_name
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                """;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int countWhere(String table, String where) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
    }

    private Long nextId(String table, String column) {
        Number max = jdbc.queryForObject("SELECT COALESCE(MAX(" + column + "), 0) + 1 FROM " + table, Number.class);
        return max.longValue();
    }

    private String requiredBranchName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("branchName is required");
        }
        return value.trim();
    }

    private String requiredCompanyName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("companyName is required");
        }
        return value.trim();
    }

    private Long requiredId(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasCustomerFilters(String companyName, String taxId, String phone, String ownerName,
                                       Long branchId, String officeNo) {
        return !blankToEmpty(companyName).isBlank()
                || !blankToEmpty(taxId).isBlank()
                || !blankToEmpty(phone).isBlank()
                || !blankToEmpty(ownerName).isBlank()
                || branchId != null
                || !blankToEmpty(officeNo).isBlank();
    }

    private Integer normalizeStatus(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0 || value > 2) {
            throw new IllegalArgumentException("status must be 0, 1, or 2");
        }
        return value;
    }

    private String normalizeRentalItem(String value, String fallback) {
        String item = blankToNull(value);
        if (item == null) {
            item = normalizeRegistrationType(fallback, null);
        }
        if (item == null) {
            return "登記";
        }
        if ("實體辦公室".equals(item)) {
            return "辦公室";
        }
        if (!List.of("辦公室", "座位", "登記", "聯絡處").contains(item)) {
            throw new IllegalArgumentException("rentalItem must be 辦公室, 座位, 登記, or 聯絡處");
        }
        return item;
    }

    private Integer normalizeRentalStatus(Integer value) {
        if (value == null) {
            return 1;
        }
        if (value < 1 || value > 3) {
            throw new IllegalArgumentException("rentalStatus must be 1, 2, or 3");
        }
        return value;
    }

    private String normalizeRegistrationType(String value, String fallback) {
        String text = blankToNull(value);
        if (text != null) {
            return "實體辦公室".equals(text) ? "辦公室" : text;
        }
        String item = blankToNull(fallback);
        return item == null ? null : ("實體辦公室".equals(item) ? "辦公室" : item);
    }

    private String normalizeContractRentalItem(String value) {
        String item = blankToNull(value);
        if (item == null) {
            return null;
        }
        if ("實體辦公室".equals(item)) {
            return "辦公室";
        }
        if (!List.of("辦公室", "座位", "登記", "聯絡處").contains(item)) {
            throw new IllegalArgumentException("rentalItem must be 辦公室, 座位, 登記, or 聯絡處");
        }
        return item;
    }

    private String normalizeContractRentalStatus(String value) {
        String status = blankToNull(value);
        if (status == null) {
            return null;
        }
        if (!List.of("登記", "辦公室", "登記+辦公室").contains(status)) {
            throw new IllegalArgumentException("rentalStatus must be 登記, 辦公室, or 登記+辦公室");
        }
        return status;
    }

    private String normalizeContractStatus(String value) {
        if (value == null || value.isBlank()) {
            return "綁約中";
        }
        String status = value.trim();
        if (!"綁約中".equals(status) && !"已解約".equals(status)) {
            throw new IllegalArgumentException("leaseStatus must be 綁約中 or 已解約");
        }
        return status;
    }

    private String saveLeaseImage(Long contractId, MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "contract-image" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = "contract-" + contractId + "-" + System.currentTimeMillis() + "-" + safeName;
        Path uploadDir = Path.of("uploads", "contracts");
        Path target = uploadDir.resolve(fileName).normalize();
        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return uploadDir.resolve(fileName).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "lease image upload failed", e);
        }
    }

    private boolean contractOverlaps(Map<String, Object> row, String queryStartText, String queryEndText) {
        Integer queryStart = dateNumber(queryStartText);
        Integer queryEnd = dateNumber(queryEndText);
        if (queryStart == null && queryEnd == null) {
            return true;
        }
        Integer contractStart = dateNumber((String) row.get("start_date_text"));
        Integer contractEnd = dateNumber((String) row.get("end_date_text"));
        if (queryStart != null && contractEnd != null && queryStart > contractEnd) {
            return false;
        }
        return queryEnd == null || contractStart == null || queryEnd >= contractStart;
    }

    private boolean shouldShowExpirationReminder(String endDateText, LocalDate today) {
        LocalDate endDate = localDate(endDateText);
        if (endDate == null) {
            return false;
        }
        LocalDate reminderStart = endDate.minusMonths(1).withDayOfMonth(1);
        return !today.isBefore(reminderStart) && !today.isAfter(endDate);
    }

    private boolean isBirthdayInMonth(String birthdayText, LocalDate today) {
        MonthDay birthday = monthDay(birthdayText);
        return birthday != null && birthday.getMonthValue() == today.getMonthValue();
    }

    private MonthDay monthDay(String value) {
        LocalDate date = localDate(value);
        return date == null ? null : MonthDay.from(date);
    }

    private LocalDate localDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(text);
            }
            if (text.matches("\\d{2,3}\\.\\d{1,2}\\.\\d{1,2}")) {
                String[] parts = text.split("\\.");
                int year = Integer.parseInt(parts[0]) + 1911;
                return LocalDate.of(year, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            if (text.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
                String[] parts = text.split("/");
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private Integer dateNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return Integer.parseInt(text.replace("-", ""));
        }
        if (text.matches("\\d{2,3}\\.\\d{1,2}\\.\\d{1,2}")) {
            String[] parts = text.split("\\.");
            int year = Integer.parseInt(parts[0]) + 1911;
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return year * 10000 + month * 100 + day;
        }
        if (text.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
            String[] parts = text.split("/");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return year * 10000 + month * 100 + day;
        }
        return null;
    }

    private boolean passwordMatches(String storedPassword, String rawPassword) {
        if (storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith("{noop}")) {
            return storedPassword.substring("{noop}".length()).equals(rawPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    private void applyPermissions(Map<String, Object> user) {
        String roleName = (String) user.get("role_name");
        user.put("canCreateRent", "主管".equals(roleName));
        user.put("canEditRent", !"一般秘書".equals(roleName));
        user.put("canEditStaff", "主管".equals(roleName));
        user.put("canCreateOffice", true);
        user.put("canEditAllBranches", "主管".equals(roleName));
        user.put("canViewAllOffices", !"一般秘書".equals(roleName));
        user.put("canManageBranch", "主管".equals(roleName));
    }
}
