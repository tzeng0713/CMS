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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
                .andExpect(jsonPath("$.notifications.incompleteContracts").isArray())
                .andExpect(jsonPath("$.notifications.ownerBirthdays").doesNotExist());

        mvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company_name").exists());
    }

    @Test
    void dashboardReturnsContractExpirationNotificationsWithoutBirthdays() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        LocalDate contractEnd = today.plusDays(30);
        String activeCompany = "Dashboard Expiring Active Co";
        String endedCompany = "Dashboard Expiring Ended Co";
        long activeCustomerId = insertDashboardCustomer(activeCompany, "1978-01-01");
        long endedCustomerId = insertDashboardCustomer(endedCompany, "1978-01-01");
        insertDashboardContract(activeCustomerId, contractEnd, "綁約中");
        insertDashboardContract(endedCustomerId, contractEnd, "已解約");

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.expiringContracts[*].company_name", hasItems(activeCompany)))
                .andExpect(jsonPath("$.notifications.expiringContracts[*].company_name").value(org.hamcrest.Matchers.not(hasItems(endedCompany))))
                .andExpect(jsonPath("$.notifications.ownerBirthdays").doesNotExist());
    }

    @Test
    void customerSearchFiltersByBirthdayMonth() throws Exception {
        long augustOwner = insertDashboardCustomer("August Birthday Search Co", "1988/8/12");
        long septemberContact = insertDashboardCustomer("September Contact Search Co", "1980-01-01");
        long rocContact = insertDashboardCustomer("ROC Contact Search Co", "1980-01-01");
        jdbc.update("UPDATE customers SET contact_birthday = '1992-09-05' WHERE customer_id = ?", septemberContact);
        jdbc.update("UPDATE customers SET contact_birthday = '115.9.18' WHERE customer_id = ?", rocContact);

        mvc.perform(get("/api/customers").param("ownerBirthdayMonth", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customer_id", hasItem((int) augustOwner)))
                .andExpect(jsonPath("$[*].customer_id", not(hasItem((int) septemberContact))));

        mvc.perform(get("/api/customers")
                        .param("contactBirthdayMonth", "9")
                        .param("companyName", "Contact Search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customer_id", hasItems((int) septemberContact, (int) rocContact)))
                .andExpect(jsonPath("$[*].customer_id", not(hasItem((int) augustOwner))));
    }

    @Test
    void customerBirthdaySearchDoesNotTruncateCandidatesBeforeFiltering() throws Exception {
        long targetCustomer = insertDashboardCustomer("Older November Birthday Co", "1985-11-20");
        for (int index = 0; index < 1000; index++) {
            insertDashboardCustomer("Newer January Birthday Co " + index, "1985-01-20");
        }

        mvc.perform(get("/api/customers").param("ownerBirthdayMonth", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customer_id", hasItem((int) targetCustomer)));
    }

    @Test
    void dashboardUsesLatestContractsForServiceCountsAndExpiration() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        int officeBefore = objectMapper.readTree(mvc.perform(get("/api/dashboard")).andReturn()
                .getResponse().getContentAsString()).path("officeCustomers").asInt();
        int registrationBefore = objectMapper.readTree(mvc.perform(get("/api/dashboard")).andReturn()
                .getResponse().getContentAsString()).path("registrationCustomers").asInt();

        long officeCustomer = insertDashboardCustomer("Latest Office Count Co", "1980-01-01");
        long combinedCustomer = insertDashboardCustomer("Latest Combined Count Co", "1980-01-01");
        insertDashboardContractWithTerms(officeCustomer, today.minusMonths(10), today.plusDays(20),
                "登記", 1, 1000, "綁約中");
        insertDashboardContractWithTerms(officeCustomer, today, today.plusMonths(8),
                "辦公室", 3, 5000, "綁約中");
        insertDashboardContractWithTerms(combinedCustomer, today, today.plusDays(45),
                "登記+辦公室", 6, 3000, "綁約中");

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officeCustomers", is(officeBefore + 2)))
                .andExpect(jsonPath("$.registrationCustomers", is(registrationBefore + 1)))
                .andExpect(jsonPath("$.notifications.expiringContracts[*].company_name",
                        hasItem("Latest Combined Count Co")))
                .andExpect(jsonPath("$.notifications.expiringContracts[*].company_name",
                        not(hasItem("Latest Office Count Co"))))
                .andExpect(jsonPath("$.notifications.expiringContracts[?(@.company_name == 'Latest Combined Count Co')].rental_item",
                        hasItem("登記+辦公室")));

        long outsideCustomer = insertDashboardCustomer("Outside 45 Day Co", "1980-01-01");
        insertDashboardContractWithTerms(outsideCustomer, today, today.plusDays(46),
                "登記", 1, 1000, "綁約中");
        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.notifications.expiringContracts[*].company_name",
                        not(hasItem("Outside 45 Day Co"))));
    }

    @Test
    void dashboardShowsUnpaidRentThirtyDaysBeforeNextPeriodWithoutDuplicatingIncompleteContracts() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        long unpaidCustomer = insertDashboardCustomer("Recurring Rent Reminder Co", "1980-01-01");
        long unpaidContract = insertDashboardContractWithTerms(unpaidCustomer, today.minusMonths(6),
                today.plusMonths(8), "辦公室", 3, 2000, "綁約中");
        insertDashboardRentPayment(unpaidCustomer, unpaidContract, today.minusMonths(3), today.plusDays(29));

        long incompleteCustomer = insertDashboardCustomer("Only Incomplete Reminder Co", "1980-01-01");
        insertDashboardContractWithTerms(incompleteCustomer, today, today.plusMonths(8),
                "登記", 6, 3000, "綁約中");

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.unpaidRent[*].company_name",
                        hasItem("Recurring Rent Reminder Co")))
                .andExpect(jsonPath("$.notifications.unpaidRent[*].company_name",
                        not(hasItem("Only Incomplete Reminder Co"))))
                .andExpect(jsonPath("$.notifications.incompleteContracts[*].company_name",
                        hasItem("Only Incomplete Reminder Co")))
                .andExpect(jsonPath("$.notifications.unpaidRent[?(@.company_name == 'Recurring Rent Reminder Co')].suggested_amount",
                        hasItem(6000.0)));

        insertDashboardRentPayment(unpaidCustomer, unpaidContract, today.plusDays(30), today.plusMonths(3));
        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.notifications.unpaidRent[*].company_name",
                        not(hasItem("Recurring Rent Reminder Co"))));
    }

    @Test
    void dashboardKeepsOverdueRentVisibleAfterContractEnd() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        long customerId = insertDashboardCustomer("Expired Contract Debt Co", "1980-01-01");
        long contractId = insertDashboardContractWithTerms(customerId, today.minusMonths(6),
                today.minusDays(1), "辦公室", 1, 2000, "綁約中");
        insertDashboardRentPayment(customerId, contractId, today.minusMonths(2), today.minusMonths(1));

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.unpaidRent[*].company_name",
                        hasItem("Expired Contract Debt Co")));
    }

    @Test
    void dashboardNotificationListsAreNotTruncatedBeforeClientPagination() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        for (int index = 0; index < 51; index++) {
            long customerId = insertDashboardCustomer("Pagination Expiration Co " + index, "1980-01-01");
            insertDashboardContractWithTerms(customerId, today.minusMonths(6), today.plusDays(30),
                    "登記", 1, 1000, "綁約中");
        }

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.expiringContracts.length()", greaterThan(50)));
    }

    @Test
    void integratedCustomerCreationStoresNewFieldsRelationsAndFirstPayment() throws Exception {
        MockMultipartFile payload = new MockMultipartFile("payload", "", MediaType.TEXT_PLAIN_VALUE, """
                {
                  "customer": {
                    "companyName": "Workflow Alpha Co",
                    "taxId": "WF-A-001",
                    "status": 0,
                    "rentalItem": "停業",
                    "rentalStatus": 1,
                    "ownerName": "Alpha Owner",
                    "contactPerson": "Alpha Contact",
                    "contactBirthday": "1992-04-05",
                    "accountInfo": "銀行末五碼 54321",
                    "isAgent": true,
                    "accountantInfo": "王會計師",
                    "relatedCompanyNames": ["Workflow Beta Co", "Workflow Gamma Co"],
                    "updatedBy": 1
                  },
                  "contract": {
                    "officeId": null,
                    "rentalItem": "停業",
                    "rentalStatus": "個人名義",
                    "signedDateText": "2026-07-18",
                    "signerStaffId": 1,
                    "partnerStaffId": 2,
                    "sourceText": "舊客戶介紹",
                    "paymentMonths": 6,
                    "startDateText": "2026-08-01",
                    "endDateText": "2027-01-31",
                    "rent": 3500,
                    "deposit": 7000,
                    "leaseStatus": "綁約中",
                    "updatedBy": 1
                  },
                  "firstPaymentAmount": 3500,
                  "firstPaymentDateText": "2026-07-19"
                }
                """.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/customers/with-contract").file(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_info", is("銀行末五碼 54321")))
                .andExpect(jsonPath("$.is_agent", is(true)))
                .andExpect(jsonPath("$.contact_birthday", is("1992-04-05")))
                .andExpect(jsonPath("$.accountant_info", is("王會計師")))
                .andExpect(jsonPath("$.relatedCompanies[*].company_name", hasItems("Workflow Beta Co", "Workflow Gamma Co")))
                .andExpect(jsonPath("$.contracts[0].source_text", is("舊客戶介紹")))
                .andExpect(jsonPath("$.contracts[0].partner_staff_id", is(2)))
                .andExpect(jsonPath("$.contracts[0].partner_staff_name").exists())
                .andExpect(jsonPath("$.rentPayments[0].amount", is(3500.0)))
                .andExpect(jsonPath("$.rentPayments[0].payment_date_text", is("2026-07-19")))
                .andExpect(jsonPath("$.rentPayments[0].fee_start_date_text", is("2026-08-01")))
                .andExpect(jsonPath("$.rentPayments[0].fee_end_date_text", is("2027-01-31")));
    }

    @Test
    void integratedCustomerCreationRequiresBothFirstPaymentFieldsAndRollsBack() throws Exception {
        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE company_name = 'Incomplete Payment Co'", Integer.class);
        MockMultipartFile payload = new MockMultipartFile("payload", "", MediaType.TEXT_PLAIN_VALUE, """
                {
                  "customer": {
                    "companyName": "Incomplete Payment Co",
                    "updatedBy": 1
                  },
                  "contract": {
                    "rentalItem": "登記",
                    "rentalStatus": "登記",
                    "signerStaffId": 1,
                    "leaseStatus": "綁約中",
                    "updatedBy": 1
                  },
                  "firstPaymentAmount": 3000
                }
                """.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/customers/with-contract").file(payload))
                .andExpect(status().isBadRequest());

        Integer after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE company_name = 'Incomplete Payment Co'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(before, after);
    }

    @Test
    void integratedCustomerCreationRejectsReversedContractDates() throws Exception {
        MockMultipartFile payload = new MockMultipartFile("payload", "", MediaType.TEXT_PLAIN_VALUE, """
                {
                  "customer": {"companyName": "Reversed Contract Dates Co", "updatedBy": 1},
                  "contract": {
                    "rentalItem": "登記",
                    "rentalStatus": "登記",
                    "startDateText": "2026-08-01",
                    "endDateText": "2026-07-31",
                    "paymentMonths": 1,
                    "leaseStatus": "綁約中",
                    "updatedBy": 1
                  },
                  "firstPaymentAmount": 3000,
                  "firstPaymentDateText": "2026-07-19"
                }
                """.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/customers/with-contract").file(payload))
                .andExpect(status().isBadRequest());
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE company_name = 'Reversed Contract Dates Co'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, count);
    }

    @Test
    void contractRejectsSameSignerAndPartner() throws Exception {
        mvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 1,
                                  "rentalItem": "登記",
                                  "rentalStatus": "登記",
                                  "signerStaffId": 1,
                                  "partnerStaffId": 1,
                                  "leaseStatus": "綁約中",
                                  "updatedBy": 1
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void relatedCompaniesResolveAndDisplayBidirectionally() throws Exception {
        MvcResult betaResult = mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Relation Beta Co",
                                  "relatedCompanyNames": ["Relation Alpha Co", "Relation Gamma Co"],
                                  "updatedBy": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedCompanies[*].company_name",
                        hasItems("Relation Alpha Co", "Relation Gamma Co")))
                .andReturn();
        long betaId = objectMapper.readTree(betaResult.getResponse().getContentAsString())
                .get("customer_id").asLong();

        MvcResult alphaResult = mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Relation Alpha Co",
                                  "updatedBy": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long alphaId = objectMapper.readTree(alphaResult.getResponse().getContentAsString())
                .get("customer_id").asLong();

        mvc.perform(get("/api/customers/{id}", alphaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedCompanies[*].company_name", hasItem("Relation Beta Co")));
        mvc.perform(get("/api/customers/{id}", betaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedCompanies[*].customer_id", hasItem((int) alphaId)));
    }

    @Test
    void latestContractReturnsNewestContractForRenewal() throws Exception {
        MvcResult created = mvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 1,
                                  "officeId": 2,
                                  "rentalItem": "停業",
                                  "rentalStatus": "個人名義",
                                  "signedDateText": "2026-07-19",
                                  "signerStaffId": 1,
                                  "partnerStaffId": 2,
                                  "sourceText": "續約測試",
                                  "paymentMonths": 6,
                                  "rent": 7777,
                                  "deposit": 8888,
                                  "leaseStatus": "綁約中",
                                  "updatedBy": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        int contractId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("contract_id").asInt();

        mvc.perform(get("/api/customers/1/latest-contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_id", is(contractId)))
                .andExpect(jsonPath("$.rental_item", is("停業")))
                .andExpect(jsonPath("$.rental_status", is("個人名義")))
                .andExpect(jsonPath("$.source_text", is("續約測試")))
                .andExpect(jsonPath("$.partner_staff_id", is(2)));
    }

    @Test
    void dashboardIncompleteContractReminderDisappearsAfterPayment() throws Exception {
        long customerId = insertDashboardCustomer("Unpaid Contract Reminder Co", "1980-01-01");
        insertDashboardContract(customerId, LocalDate.now().plusMonths(3), "綁約中");
        int contractId = jdbc.queryForObject(
                "SELECT MAX(contract_id) FROM contracts WHERE customer_id = ?", Integer.class, customerId);

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.incompleteContracts[*].company_name",
                        hasItem("Unpaid Contract Reminder Co")));

        mvc.perform(post("/api/rent-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "contractId": %d,
                                  "paymentDateText": "2026-07-19",
                                  "amount": 1000,
                                  "updatedBy": 1
                                }
                                """.formatted(customerId, contractId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.incompleteContracts[*].company_name",
                        not(hasItem("Unpaid Contract Reminder Co"))));
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

    private long insertDashboardContractWithTerms(long customerId, LocalDate startDate, LocalDate endDate,
                                                  String rentalStatus, int paymentMonths, int rent,
                                                  String leaseStatus) {
        Long contractId = jdbc.queryForObject("SELECT COALESCE(MAX(contract_id), 0) + 1 FROM contracts", Long.class);
        jdbc.update("""
                INSERT INTO contracts (
                    contract_id, customer_id, office_id, rental_item, rental_status, payment_months,
                    start_date_text, end_date_text, rent, deposit, lease_status, updated_by
                ) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, contractId, customerId, rentalStatus, rentalStatus, paymentMonths,
                startDate.toString(), endDate.toString(), rent, rent, leaseStatus);
        return contractId;
    }

    private void insertDashboardRentPayment(long customerId, long contractId,
                                            LocalDate feeStartDate, LocalDate feeEndDate) {
        Long paymentId = jdbc.queryForObject(
                "SELECT COALESCE(MAX(rent_payment_id), 0) + 1 FROM rent_payments", Long.class);
        jdbc.update("""
                INSERT INTO rent_payments (
                    rent_payment_id, customer_id, contract_id, payment_month, payment_date_text,
                    fee_start_date_text, fee_end_date_text, amount, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 6000, 1)
                """, paymentId, customerId, contractId,
                feeStartDate.getYear() * 100 + feeStartDate.getMonthValue(),
                feeStartDate.toString(), feeStartDate.toString(), feeEndDate.toString());
    }
}
