package com.example.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CmsApplicationTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dashboardAndCustomersLoadSeedData() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", greaterThan(0)))
                .andExpect(jsonPath("$.notifications.expiringContracts").isArray())
                .andExpect(jsonPath("$.notifications.ownerBirthdays").isArray());

        mvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company_name").exists());
    }

    @Test
    void dashboardReturnsContractExpirationAndBirthdayNotifications() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        LocalDate contractEnd = today.plusMonths(1).withDayOfMonth(28);
        String activeCompany = "Dashboard Expiring Active Co";
        String endedCompany = "Dashboard Expiring Ended Co";
        String birthdayCompany = "Dashboard Birthday Co";
        long activeCustomerId = insertDashboardCustomer(activeCompany, "1978-01-01");
        long endedCustomerId = insertDashboardCustomer(endedCompany, "1978-01-01");
        long birthdayCustomerId = insertDashboardCustomer(birthdayCompany,
                "1988-%02d-15".formatted(today.getMonthValue()));
        insertDashboardContract(activeCustomerId, contractEnd, "綁約中");
        insertDashboardContract(endedCustomerId, contractEnd, "已解約");

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.expiringContracts[*].company_name", hasItems(activeCompany)))
                .andExpect(jsonPath("$.notifications.expiringContracts[*].company_name").value(org.hamcrest.Matchers.not(hasItems(endedCompany))))
                .andExpect(jsonPath("$.notifications.ownerBirthdays[*].company_name", hasItems(birthdayCompany)));
    }

    @Test
    void customerSearchIncludesCustomersWithSameOwnerName() throws Exception {
        mvc.perform(get("/api/customers").param("search", "金鋐源"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customer_id", hasItems(2, 3, 4)));
    }

    @Test
    void customersCanBeFilteredByDetailedConditions() throws Exception {
        mvc.perform(get("/api/customers")
                        .param("companyName", "金鋐源")
                        .param("taxId", "772149")
                        .param("phone", "羅")
                        .param("ownerName", "羅至")
                        .param("branchId", "1")
                        .param("officeNo", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customer_id", is(2)))
                .andExpect(jsonPath("$[0].company_name", is("金鋐源記帳士事務所")));
    }

    @Test
    void customerCanBeCreatedUpdatedAndLookedUp() throws Exception {
        MvcResult createResult = mvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "companyName": "Test Customer Co",
                                  "taxId": "TEST001",
                                  "status": 0,
                                  "rentalItem": "登記",
                                  "rentalStatus": 1,
                                  "ownerName": "Owner One",
                                  "ownerBirthday": "1988-01-02",
                                  "contactPerson": "Contact One",
                                  "phone": "0911000000",
                                  "forwardingAddress": "Taipei",
                                  "pettyCash": 1200,
                                  "referrer": "Ref One",
                                  "notes": "created by test",
                                  "registrationType": "登記",
                                  "updatedBy": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company_name", is("Test Customer Co")))
                .andExpect(jsonPath("$.tax_id", is("TEST001")))
                .andExpect(jsonPath("$.rental_item", is("登記")))
                .andExpect(jsonPath("$.rental_status", is(1)))
                .andExpect(jsonPath("$.owner_birthday", is("1988-01-02")))
                .andExpect(jsonPath("$.referrer", is("Ref One")))
                .andReturn();

        long customerId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("customer_id")
                .asLong();

        mvc.perform(put("/api/customers/{id}", customerId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "companyName": "Edited Customer Co",
                                  "taxId": "TEST002",
                                  "status": 1,
                                  "rentalItem": "辦公室",
                                  "rentalStatus": 3,
                                  "ownerName": "Owner Two",
                                  "ownerBirthday": "1990-03-04",
                                  "contactPerson": "Contact Two",
                                  "phone": "0922000000",
                                  "forwardingAddress": "New Taipei",
                                  "pettyCash": 500,
                                  "referrer": "Ref Two",
                                  "notes": "updated by test",
                                  "registrationType": "辦公室",
                                  "updatedBy": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company_name", is("Edited Customer Co")))
                .andExpect(jsonPath("$.tax_id", is("TEST002")))
                .andExpect(jsonPath("$.status", is(1)))
                .andExpect(jsonPath("$.rental_item", is("辦公室")))
                .andExpect(jsonPath("$.rental_status", is(3)))
                .andExpect(jsonPath("$.owner_name", is("Owner Two")))
                .andExpect(jsonPath("$.owner_birthday", is("1990-03-04")))
                .andExpect(jsonPath("$.referrer", is("Ref Two")));

        mvc.perform(get("/api/customers/lookup").param("search", "TEST002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company_name", is("Edited Customer Co")));
    }

    @Test
    void rentPaymentsCanBeFilteredByCustomerKeyword() throws Exception {
        mvc.perform(get("/api/rent-payments").param("search", "77214901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tax_id", is("77214901")));
    }

    @Test
    void staffCanLoginAndRegister() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "account": "manager",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role_name", is("主管")))
                .andExpect(jsonPath("$.canCreateRent", is(true)))
                .andExpect(jsonPath("$.canEditRent", is(true)))
                .andExpect(jsonPath("$.canEditStaff", is(true)));

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "staffName": "Test Secretary",
                                  "account": "test.secretary",
                                  "password": "secret123",
                                  "roleName": "一般秘書"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff_name", is("Test Secretary")))
                .andExpect(jsonPath("$.role_name", is("一般秘書")))
                .andExpect(jsonPath("$.canCreateRent", is(false)))
                .andExpect(jsonPath("$.canEditRent", is(false)))
                .andExpect(jsonPath("$.canEditStaff", is(false)));
    }

    @Test
    void rentPaymentCanBeUpdated() throws Exception {
        mvc.perform(put("/api/rent-payments/1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMonth": 202607,
                                  "paymentDateText": "2026-07-02",
                                  "feeStartDateText": "2026-07-01",
                                  "feeEndDateText": "2026-07-31",
                                  "amount": 4321,
                                  "receiptNo": "EDIT-001",
                                  "note": "edited by test",
                                  "updatedBy": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_month", is(202607)))
                .andExpect(jsonPath("$.receipt_no", is("EDIT-001")))
                .andExpect(jsonPath("$.note", is("edited by test")));
    }

    @Test
    void contractCanBeCreatedAndUpdated() throws Exception {
        MvcResult createResult = mvc.perform(post("/api/contracts")
                        .contentType("application/json")
                        .content("""
                                {
                                  "customerId": 1,
                                  "officeId": 1,
                                  "paymentMonths": 6,
                                  "startDateText": "2026-01-01",
                                  "endDateText": "2026-06-30",
                                  "terminationDateText": "",
                                  "rent": 12000,
                                  "deposit": 24000,
                                  "leaseStatus": "綁約中",
                                  "updatedBy": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer_id", is(1)))
                .andExpect(jsonPath("$.office_id", is(1)))
                .andExpect(jsonPath("$.payment_months", is(6)))
                .andExpect(jsonPath("$.lease_status", is("綁約中")))
                .andReturn();

        long contractId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("contract_id")
                .asLong();

        mvc.perform(put("/api/contracts/{id}", contractId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "customerId": 1,
                                  "officeId": 2,
                                  "paymentMonths": 12,
                                  "startDateText": "2026-02-01",
                                  "endDateText": "2027-01-31",
                                  "terminationDateText": "2026-12-31",
                                  "rent": 15000,
                                  "deposit": 30000,
                                  "leaseStatus": "已解約",
                                  "updatedBy": 1
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.office_id", is(1)))
                .andExpect(jsonPath("$.payment_months", is(6)))
                .andExpect(jsonPath("$.termination_date_text", nullValue()))
                .andExpect(jsonPath("$.lease_status", is("已解約")));

        mvc.perform(get("/api/contracts")
                        .param("startDateText", "2026-03-01")
                        .param("endDateText", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].contract_id", hasItems((int) contractId)));
    }

    @Test
    void customerWithContractCanBeCreatedWithoutOfficeAndWithSigner() throws Exception {
        MockMultipartFile payload = new MockMultipartFile("payload", "", MediaType.TEXT_PLAIN_VALUE, """
                {
                  "customer": {
                    "companyName": "Integrated Customer Co",
                    "taxId": "INT001",
                    "status": 0,
                    "ownerName": "Integrated Owner",
                    "ownerBirthday": "1980-05-06",
                    "contactPerson": "Integrated Contact",
                    "phone": "0900111222",
                    "forwardingAddress": "Taipei",
                    "pettyCash": null,
                    "referrer": "Referral",
                    "notes": "created with contract",
                    "updatedBy": 1
                  },
                  "contract": {
                    "officeId": null,
                    "rentalItem": "登記",
                    "rentalStatus": "登記",
                    "signedDateText": "2026-06-01",
                    "signerStaffId": 1,
                    "paymentMonths": 6,
                    "startDateText": "2026-07-01",
                    "endDateText": "2026-12-31",
                    "terminationDateText": "",
                    "rent": 6000,
                    "deposit": 12000,
                    "leaseStatus": "綁約中",
                    "updatedBy": 1
                  }
                }
                """.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/customers/with-contract").file(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company_name", is("Integrated Customer Co")))
                .andExpect(jsonPath("$.contracts[0].office_id", nullValue()))
                .andExpect(jsonPath("$.contracts[0].rental_item", is("登記")))
                .andExpect(jsonPath("$.contracts[0].rental_status", is("登記")))
                .andExpect(jsonPath("$.contracts[0].signed_date_text", is("2026-06-01")))
                .andExpect(jsonPath("$.contracts[0].signer_staff_id", is(1)))
                .andExpect(jsonPath("$.contracts[0].signer_staff_name").exists());
    }

    @Test
    void customerWithContractStoresLeaseImagePath() throws Exception {
        MockMultipartFile payload = new MockMultipartFile("payload", "", MediaType.TEXT_PLAIN_VALUE, """
                {
                  "customer": {
                    "companyName": "Image Contract Co",
                    "taxId": "IMG001",
                    "status": 0,
                    "ownerName": "Image Owner",
                    "updatedBy": 1
                  },
                  "contract": {
                    "officeId": 1,
                    "rentalItem": "辦公室",
                    "rentalStatus": "辦公室",
                    "signedDateText": "2026-06-02",
                    "signerStaffId": 1,
                    "paymentMonths": 3,
                    "startDateText": "2026-07-01",
                    "endDateText": "2026-09-30",
                    "rent": 9000,
                    "deposit": 18000,
                    "leaseStatus": "綁約中",
                    "updatedBy": 1
                  }
                }
                """.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile image = new MockMultipartFile(
                "leaseImage", "lease.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3});

        MvcResult result = mvc.perform(multipart("/api/customers/with-contract").file(payload).file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contracts[0].lease_image_path").exists())
                .andReturn();

        String imagePath = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("contracts")
                .get(0)
                .get("lease_image_path")
                .asText();
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(Path.of(imagePath)));
    }

    @Test
    void customerWithContractRollsBackWhenContractIsInvalid() throws Exception {
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE company_name = 'Rollback Customer Co'", Integer.class);
        MockMultipartFile payload = new MockMultipartFile("payload", "", MediaType.TEXT_PLAIN_VALUE, """
                {
                  "customer": {
                    "companyName": "Rollback Customer Co",
                    "taxId": "RB001",
                    "status": 0,
                    "ownerName": "Rollback Owner",
                    "updatedBy": 1
                  },
                  "contract": {
                    "officeId": null,
                    "rentalItem": "登記",
                    "rentalStatus": "登記",
                    "signerStaffId": 1,
                    "leaseStatus": "不是合法狀態",
                    "updatedBy": 1
                  }
                }
                """.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/customers/with-contract").file(payload))
                .andExpect(status().isBadRequest());

        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE company_name = 'Rollback Customer Co'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(before, after);
    }

    @Test
    void staffCanBeFilteredAndRoleUpdated() throws Exception {
        mvc.perform(get("/api/staff").param("branchId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branch_name").exists())
                .andExpect(jsonPath("$[0].role_name").exists());

        mvc.perform(put("/api/staff/3")
                        .contentType("application/json")
                        .content("""
                                {
                                  "rolePermissionId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff_id", is(3)))
                .andExpect(jsonPath("$.role_permission_id", is(2)))
                .andExpect(jsonPath("$.role_name", is("督導秘書")));
    }

    private long insertDashboardCustomer(String companyName, String ownerBirthday) {
        Long customerId = jdbc.queryForObject("SELECT COALESCE(MAX(customer_id), 0) + 1 FROM customers", Long.class);
        jdbc.update("""
                INSERT INTO customers (
                    customer_id, company_name, status, rental_item, rental_status,
                    owner_name, owner_birthday, contact_person, phone, registration_type, updated_by
                ) VALUES (?, ?, 0, '登記', 1, ?, ?, ?, '0900000000', '登記', 1)
                """, customerId, companyName, companyName + " Owner", ownerBirthday, companyName + " Contact");
        return customerId;
    }

    private void insertDashboardContract(long customerId, LocalDate endDate, String leaseStatus) {
        Long contractId = jdbc.queryForObject("SELECT COALESCE(MAX(contract_id), 0) + 1 FROM contracts", Long.class);
        jdbc.update("""
                INSERT INTO contracts (
                    contract_id, customer_id, office_id, payment_months, start_date_text, end_date_text,
                    rent, deposit, lease_status, updated_by
                ) VALUES (?, ?, 1, 1, ?, ?, 1000, 1000, ?, 1)
                """, contractId, customerId, endDate.minusMonths(11).toString(), endDate.toString(), leaseStatus);
    }
}
