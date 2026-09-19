package com.example.cms;

import com.example.cms.config.SchemaMigrationRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                .andExpect(jsonPath("$.content[0].company_name").exists())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.pageSize", is(20)));
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
                .andExpect(jsonPath("$.content[*].customer_id", hasItem((int) augustOwner)))
                .andExpect(jsonPath("$.content[*].customer_id", not(hasItem((int) septemberContact))));

        mvc.perform(get("/api/customers")
                        .param("contactBirthdayMonth", "9")
                .param("companyName", "Contact Search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].customer_id", hasItems((int) septemberContact, (int) rocContact)))
                .andExpect(jsonPath("$.content[*].customer_id", not(hasItem((int) augustOwner))));
    }

    @Test
    void customerBirthdaySearchDoesNotTruncateCandidatesBeforeFiltering() throws Exception {
        long targetCustomer = insertDashboardCustomer("Older November Birthday Co", "1985-11-20");
        for (int index = 0; index < 1000; index++) {
            insertDashboardCustomer("Newer January Birthday Co " + index, "1985-01-20");
        }

        mvc.perform(get("/api/customers")
                        .param("ownerBirthdayMonth", "11")
                        .param("page", "0")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].customer_id", hasItem((int) targetCustomer)))
                .andExpect(jsonPath("$.totalElements", is(1)));
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
    void contractRenewalStoresTheCurrentPaymentAmountAndDate() throws Exception {
        MvcResult created = mvc.perform(post("/api/contracts/with-first-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contract": {
                                    "customerId": 1,
                                    "rentalItem": "登記",
                                    "rentalStatus": "登記",
                                    "signerStaffId": 1,
                                    "partnerStaffId": 2,
                                    "paymentMonths": 1,
                                    "startDateText": "2026-08-01",
                                    "endDateText": "2026-08-31",
                                    "rent": 3000,
                                    "leaseStatus": "綁約中",
                                    "updatedBy": 1
                                  },
                                  "firstPaymentAmount": 3000,
                                  "firstPaymentDateText": "2026-08-02"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_id").exists())
                .andReturn();

        long contractId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("contract_id").asLong();
        Map<String, Object> payment = jdbc.queryForMap(
                "SELECT * FROM rent_payments WHERE contract_id = ?", contractId);

        org.junit.jupiter.api.Assertions.assertEquals(3000.0, ((Number) payment.get("AMOUNT")).doubleValue());
        org.junit.jupiter.api.Assertions.assertEquals("2026-08-02", payment.get("PAYMENT_DATE_TEXT"));
        org.junit.jupiter.api.Assertions.assertEquals("本次繳款", payment.get("NOTE"));
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
                .andExpect(jsonPath("$.content[*].customer_id", hasItems(2, 3, 4)));
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
                .andExpect(jsonPath("$.content[0].customer_id", is(2)))
                .andExpect(jsonPath("$.content[0].company_name", is("金鋐源記帳士事務所")));
    }

    @Test
    void customerSearchSupportsAccountInfoAndReturnsLatestContractFields() throws Exception {
        mvc.perform(get("/api/customers")
                        .param("accountInfo", "第一銀行")
                        .param("page", "0")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.pageSize", is(1)))
                .andExpect(jsonPath("$.content[0].company_name", is("金鋐源記帳士事務所")))
                .andExpect(jsonPath("$.content[0].phone", is("0907637698 羅r")))
                .andExpect(jsonPath("$.content[0].tax_id", is("77214901")))
                .andExpect(jsonPath("$.content[0].rent", is(17000.0)))
                .andExpect(jsonPath("$.content[0].signed_date_text", is("2026-01-05")))
                .andExpect(jsonPath("$.content[0].end_date_text", is("2026-12-31")));
    }

    @Test
    void customerSearchReturnsOnlyRequestedPage() throws Exception {
        insertDashboardCustomer("Server Page Customer A", "1980-01-01");
        insertDashboardCustomer("Server Page Customer B", "1980-01-01");
        insertDashboardCustomer("Server Page Customer C", "1980-01-01");

        mvc.perform(get("/api/customers")
                        .param("companyName", "Server Page Customer")
                        .param("page", "0")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.pageSize", is(2)));

        mvc.perform(get("/api/customers")
                        .param("companyName", "Server Page Customer")
                        .param("page", "1")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.pageSize", is(2)));
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

        mvc.perform(get("/api/customers/lookup").param("search", "Edited Customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company_name", is("Edited Customer Co")));

        mvc.perform(get("/api/customers/lookup").param("search", "TEST002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/customers/lookup").param("search", "Owner Two"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rentPaymentsCanBeFilteredByCustomerKeyword() throws Exception {
        mvc.perform(get("/api/rent-payments").param("search", "77214901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tax_id", is("77214901")));
    }

    @Test
    void contractSearchReturnsOnlyTheRequestedServerPage() throws Exception {
        long firstCustomer = insertDashboardCustomer("Contract Server Page A", "1980-01-01");
        long secondCustomer = insertDashboardCustomer("Contract Server Page B", "1980-01-01");
        long thirdCustomer = insertDashboardCustomer("Contract Server Page C", "1980-01-01");
        insertDashboardContractWithTerms(firstCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "辦公室", 1, 1000, "綁約中");
        insertDashboardContractWithTerms(secondCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "辦公室", 1, 1000, "綁約中");
        insertDashboardContractWithTerms(thirdCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "辦公室", 1, 1000, "綁約中");

        mvc.perform(get("/api/contracts")
                        .param("companyName", "Contract Server Page")
                        .param("page", "1")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].company_name", is("Contract Server Page A")))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.pageSize", is(2)));
    }

    @Test
    void reconciliationSearchReturnsOnlyTheRequestedServerPage() throws Exception {
        long firstCustomer = insertDashboardCustomer("Reconciliation Server Page A", "1980-01-01");
        long secondCustomer = insertDashboardCustomer("Reconciliation Server Page B", "1980-01-01");
        long thirdCustomer = insertDashboardCustomer("Reconciliation Server Page C", "1980-01-01");
        long firstContract = insertDashboardContractWithTerms(firstCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "辦公室", 1, 1000, "綁約中");
        long secondContract = insertDashboardContractWithTerms(secondCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "辦公室", 1, 1000, "綁約中");
        long thirdContract = insertDashboardContractWithTerms(thirdCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "辦公室", 1, 1000, "綁約中");
        insertDashboardRentPayment(firstCustomer, firstContract, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        insertDashboardRentPayment(secondCustomer, secondContract, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 31));
        insertDashboardRentPayment(thirdCustomer, thirdContract, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 31));

        mvc.perform(get("/api/rent-payments")
                        .param("companyName", "Reconciliation Server Page")
                        .param("page", "1")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].company_name", is("Reconciliation Server Page A")))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.pageSize", is(2)));
    }

    @Test
    void rentPaymentsCanBeFilteredByCompanyTaxIdAndPaymentDateRange() throws Exception {
        long matchingCustomer = insertDashboardCustomer("Payment Filter Co", "1980-01-01");
        long outsideRangeCustomer = insertDashboardCustomer("Payment Filter Co Archive", "1980-01-01");
        jdbc.update("UPDATE customers SET tax_id = ? WHERE customer_id = ?", "PAYMENT-FILTER-TAX", matchingCustomer);
        jdbc.update("UPDATE customers SET tax_id = ? WHERE customer_id = ?", "PAYMENT-FILTER-TAX", outsideRangeCustomer);
        long matchingContract = insertDashboardContractWithTerms(
                matchingCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "辦公室", 1, 1000, "綁約中");
        long outsideRangeContract = insertDashboardContractWithTerms(
                outsideRangeCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "辦公室", 1, 1000, "綁約中");
        insertDashboardRentPayment(matchingCustomer, matchingContract, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 31));
        insertDashboardRentPayment(outsideRangeCustomer, outsideRangeContract, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 31));

        mvc.perform(get("/api/rent-payments")
                        .param("companyName", "Payment Filter Co")
                        .param("taxId", "PAYMENT-FILTER-TAX")
                        .param("paymentDateStartText", "2026-08-01")
                        .param("paymentDateEndText", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].customer_id", hasItem((int) matchingCustomer)))
                .andExpect(jsonPath("$.content[*].customer_id", not(hasItem((int) outsideRangeCustomer))));
    }

    @Test
    void rentPaymentExcelImportPreviewsEachRowBeforeWriting() throws Exception {
        long customerId = insertDashboardCustomer("Excel Preview Co", "1980-01-01");
        insertDashboardContractWithTerms(customerId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "辦公室", 1, 12000, "綁約中");

        MockMultipartFile workbook = rentPaymentWorkbook(List.of(
                new String[] { "公司名稱", "租金月份", "繳款日期", "費用起日", "費用迄日", "金額", "收據號碼", "備註" },
                new String[] { "Excel Preview Co", "2026-09", "2026-09-05", "2026-09-01", "2026-09-30", "12000", "RCPT-001", "九月租金" },
                new String[] { "不存在的客戶", "2026-09", "2026-09-05", "", "", "8000", "RCPT-002", "" }
        ));

        mvc.perform(multipart("/api/rent-payments/import-preview").file(workbook))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName", is("rent-payment-import.xlsx")))
                .andExpect(jsonPath("$.totalRows", is(2)))
                .andExpect(jsonPath("$.validRows", is(1)))
                .andExpect(jsonPath("$.errorRows", is(1)))
                .andExpect(jsonPath("$.rows[0].rowNumber", is(2)))
                .andExpect(jsonPath("$.rows[0].valid", is(true)))
                .andExpect(jsonPath("$.rows[0].paymentMonth", is("2026-09")))
                .andExpect(jsonPath("$.rows[1].valid", is(false)))
                .andExpect(jsonPath("$.rows[1].errors", hasItem("找不到客戶")));
    }

    @Test
    void rentPaymentExcelImportReportsMissingHeadersAndDuplicateRows() throws Exception {
        MockMultipartFile missingHeaderWorkbook = rentPaymentWorkbook(List.of(
                new String[] { "公司名稱", "租金月份", "繳款日期", "金額" },
                new String[] { "任意公司", "2026-09", "2026-09-05", "12000" }
        ));

        mvc.perform(multipart("/api/rent-payments/import-preview").file(missingHeaderWorkbook))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("缺少必要欄位：費用起日、費用迄日、收據號碼、備註")));

        long customerId = insertDashboardCustomer("Excel Duplicate Co", "1980-01-01");
        insertDashboardContractWithTerms(customerId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "辦公室", 1, 12000, "綁約中");
        MockMultipartFile duplicateWorkbook = rentPaymentWorkbook(List.of(
                new String[] { "公司名稱", "租金月份", "繳款日期", "費用起日", "費用迄日", "金額", "收據號碼", "備註" },
                new String[] { "Excel Duplicate Co", "2026-09", "2026-09-05", "", "", "12000", "RCPT-003", "" },
                new String[] { "Excel Duplicate Co", "2026-09", "2026-09-05", "", "", "12000", "RCPT-003", "" }
        ));

        mvc.perform(multipart("/api/rent-payments/import-preview").file(duplicateWorkbook))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validRows", is(1)))
                .andExpect(jsonPath("$.errorRows", is(1)))
                .andExpect(jsonPath("$.rows[1].errors", hasItem("Excel 內有重複的對帳資料")));
    }

    @Test
    void rentPaymentExcelImportRevalidatesAndWritesAllRowsAtomically() throws Exception {
        long customerId = insertDashboardCustomer("Excel Atomic Co", "1980-01-01");
        insertDashboardContractWithTerms(customerId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "辦公室", 1, 12000, "綁約中");
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM rent_payments", Integer.class);

        String invalidPayload = """
                {
                  "updatedBy": 1,
                  "rows": [
                    {
                      "rowNumber": 2,
                      "companyName": "Excel Atomic Co",
                      "paymentMonth": "2026-09",
                      "paymentDateText": "2026-09-05",
                      "feeStartDateText": "2026-09-01",
                      "feeEndDateText": "2026-09-30",
                      "amount": "12000",
                      "receiptNo": "RCPT-004",
                      "note": ""
                    },
                    {
                      "rowNumber": 3,
                      "companyName": "不存在的客戶",
                      "paymentMonth": "2026-09",
                      "paymentDateText": "2026-09-05",
                      "feeStartDateText": "",
                      "feeEndDateText": "",
                      "amount": "8000",
                      "receiptNo": "RCPT-005",
                      "note": ""
                    }
                  ]
                }
                """;

        mvc.perform(post("/api/rent-payments/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("第 3 列：找不到客戶")));

        int afterRejectedImport = jdbc.queryForObject("SELECT COUNT(*) FROM rent_payments", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(before, afterRejectedImport);

        String validPayload = """
                {
                  "updatedBy": 1,
                  "rows": [
                    {
                      "rowNumber": 2,
                      "companyName": "Excel Atomic Co",
                      "paymentMonth": "2026-09",
                      "paymentDateText": "2026-09-05",
                      "feeStartDateText": "2026-09-01",
                      "feeEndDateText": "2026-09-30",
                      "amount": "12000",
                      "receiptNo": "RCPT-004",
                      "note": ""
                    }
                  ]
                }
                """;

        mvc.perform(post("/api/rent-payments/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount", is(1)));

        int afterValidImport = jdbc.queryForObject("SELECT COUNT(*) FROM rent_payments", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, afterValidImport);
    }

    @Test
    void staffCanRegisterButMustVerifyEmailBeforeLoggingIn() throws Exception {
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
                                  "email": "test.secretary@cms.test"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message", is("驗證連結已寄送至註冊信箱。")));

        Integer unverified = jdbc.queryForObject("""
                SELECT COUNT(*) FROM staff
                WHERE account = 'test.secretary'
                  AND email_verified_at IS NULL
                  AND account_approved_at IS NULL
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, unverified);

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"test.secretary\",\"password\":\"secret123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationReturnsErrorsForTheSpecificInvalidField() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "staffName": "Field Error Tester",
                                  "account": "manager",
                                  "password": "secret123",
                                  "email": "field-error-account@cms.test"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.account", is("此帳號已被使用，請改用其他帳號。")));

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "staffName": "Field Error Tester",
                                  "account": "field-error-email",
                                  "password": "secret123",
                                  "email": "manager@cms.test"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email", is("此信箱已被使用，請改用其他信箱。")));

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "staffName": "Field Error Tester",
                                  "account": "field-error-password",
                                  "password": "short",
                                  "email": "field-error-password@cms.test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password", is("密碼至少需要 8 個字元。")));
    }

    @Test
    void verifiedSelfRegisteredAccountWaitsForManagerApproval() throws Exception {
        long staffId = insertPasswordResetStaff("pending-approval", "pending-approval@cms.test", "verify-password");
        jdbc.update("UPDATE staff SET account_approved_at = NULL, account_approved_by = NULL WHERE staff_id = ?", staffId);

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"pending-approval\",\"password\":\"verify-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_status", is("PENDING_APPROVAL")))
                .andExpect(jsonPath("$.is_account_approved", is(false)))
                .andExpect(jsonPath("$.canCreateOffice", is(false)));

        mvc.perform(get("/api/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].account", is("pending-approval")))
                .andExpect(jsonPath("$.content[0].account_status", is("PENDING_APPROVAL")));

        mvc.perform(patch("/api/staff/{id}/approval", staffId)
                        .contentType("application/json")
                        .content("{\"approvedByStaffId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_status", is("ACTIVE")))
                .andExpect(jsonPath("$.account_approved_by", is(1)));

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"pending-approval\",\"password\":\"verify-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_status", is("ACTIVE")))
                .andExpect(jsonPath("$.is_account_approved", is(true)))
                .andExpect(jsonPath("$.canCreateOffice", is(true)));
    }

    @Test
    void staffProfileChangesRequireReviewAndReverifyAnUpdatedEmail() throws Exception {
        long staffId = insertPasswordResetStaff("profile-change", "profile-change@cms.test", "profile-password");

        mvc.perform(post("/api/staff/{id}/profile-change-requests", staffId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestedByStaffId": %d,
                                  "staffName": "Updated Profile",
                                  "email": "updated-profile@cms.test"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.requested_staff_name", is("Updated Profile")));

        String emailBeforeApproval = jdbc.queryForObject("SELECT email FROM staff WHERE staff_id = ?", String.class, staffId);
        org.junit.jupiter.api.Assertions.assertEquals("profile-change@cms.test", emailBeforeApproval);

        mvc.perform(put("/api/staff/{id}", staffId)
                        .contentType("application/json")
                        .content("{\"rolePermissionId\":3,\"email\":\"not-allowed@cms.test\"}"))
                .andExpect(status().isBadRequest());

        Long requestId = jdbc.queryForObject("""
                SELECT MAX(staff_profile_change_request_id)
                FROM staff_profile_change_requests WHERE staff_id = ?
                """, Long.class, staffId);
        mvc.perform(patch("/api/staff/profile-change-requests/{requestId}", requestId)
                        .contentType("application/json")
                        .content("{\"reviewedByStaffId\":1,\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        Map<String, Object> staff = jdbc.queryForMap("""
                SELECT staff_name, email, email_verified_at
                FROM staff WHERE staff_id = ?
                """, staffId);
        org.junit.jupiter.api.Assertions.assertEquals("Updated Profile", staff.get("staff_name"));
        org.junit.jupiter.api.Assertions.assertEquals("updated-profile@cms.test", staff.get("email"));
        org.junit.jupiter.api.Assertions.assertNull(staff.get("email_verified_at"));
    }

    @Test
    void emailVerificationActivatesAccountOnce() throws Exception {
        long staffId = insertPasswordResetStaff("verify-once", "verify-once@cms.test", "verify-password");
        jdbc.update("UPDATE staff SET email_verified_at = NULL WHERE staff_id = ?", staffId);
        insertEmailVerificationToken(staffId, "email-verification-token", Instant.now().plusSeconds(600));

        mvc.perform(post("/api/auth/email-verifications")
                        .contentType("application/json")
                        .content("{\"token\":\"email-verification-token\"}"))
                .andExpect(status().isNoContent());

        Integer verified = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE staff_id = ? AND email_verified_at IS NOT NULL",
                Integer.class, staffId);
        org.junit.jupiter.api.Assertions.assertEquals(1, verified);

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"verify-once\",\"password\":\"verify-password\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/email-verifications")
                        .contentType("application/json")
                        .content("{\"token\":\"email-verification-token\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetRequestsAlwaysReturnAccepted() throws Exception {
        Integer tokensBefore = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class);
        mvc.perform(post("/api/auth/password-reset-requests")
                        .contentType("application/json")
                        .content("{\"identifier\":\"manager\"}"))
                .andExpect(status().isAccepted());

        Integer tokensAfterKnownAccount = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class);
        String storedHash = jdbc.queryForObject("""
                SELECT token_hash FROM password_reset_tokens
                WHERE staff_id = 1 ORDER BY password_reset_token_id DESC LIMIT 1
                """, String.class);
        org.junit.jupiter.api.Assertions.assertEquals(tokensBefore + 1, tokensAfterKnownAccount);
        org.junit.jupiter.api.Assertions.assertEquals(64, storedHash.length());

        mvc.perform(post("/api/auth/password-reset-requests")
                        .contentType("application/json")
                        .content("{\"identifier\":\"unknown-account\"}"))
                .andExpect(status().isAccepted());

        Integer tokensAfterUnknownAccount = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(tokensAfterKnownAccount, tokensAfterUnknownAccount);
    }

    @Test
    void passwordResetChangesPasswordOnceAndRejectsReplay() throws Exception {
        long staffId = insertPasswordResetStaff("reset-once", "reset-once@cms.test", "old-reset-password");
        String rawToken = "known-single-use-token";
        insertPasswordResetToken(staffId, rawToken, Instant.now().plusSeconds(600));

        mvc.perform(post("/api/auth/password-resets")
                        .contentType("application/json")
                        .content("""
                                {
                                  "token": "known-single-use-token",
                                  "password": "new-reset-password",
                                  "confirmPassword": "new-reset-password"
                                }
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"reset-once\",\"password\":\"old-reset-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"reset-once\",\"password\":\"new-reset-password\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/password-resets")
                        .contentType("application/json")
                        .content("""
                                {
                                  "token": "known-single-use-token",
                                  "password": "another-password",
                                  "confirmPassword": "another-password"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetRejectsExpiredTokensWithoutChangingPassword() throws Exception {
        long staffId = insertPasswordResetStaff("reset-expired", "reset-expired@cms.test", "still-the-password");
        insertPasswordResetToken(staffId, "expired-token", Instant.now().minusSeconds(60));

        mvc.perform(post("/api/auth/password-resets")
                        .contentType("application/json")
                        .content("""
                                {
                                  "token": "expired-token",
                                  "password": "new-reset-password",
                                  "confirmPassword": "new-reset-password"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"reset-expired\",\"password\":\"still-the-password\"}"))
                .andExpect(status().isOk());
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
                .andExpect(jsonPath("$.content[*].contract_id", hasItems((int) contractId)));
    }

    @Test
    void contractsCanBeFilteredByCustomerTaxId() throws Exception {
        long targetCustomer = insertDashboardCustomer("Contract Tax Target Co", "1980-01-01");
        long otherCustomer = insertDashboardCustomer("Contract Tax Other Co", "1980-01-01");
        jdbc.update("UPDATE customers SET tax_id = ? WHERE customer_id = ?", "CONTRACT-TAX-TARGET", targetCustomer);
        jdbc.update("UPDATE customers SET tax_id = ? WHERE customer_id = ?", "CONTRACT-TAX-OTHER", otherCustomer);
        insertDashboardContract(targetCustomer, LocalDate.now().plusMonths(6), "綁約中");
        insertDashboardContract(otherCustomer, LocalDate.now().plusMonths(6), "綁約中");

        mvc.perform(get("/api/contracts").param("taxId", "CONTRACT-TAX-TARGET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].customer_id", hasItem((int) targetCustomer)))
                .andExpect(jsonPath("$.content[*].customer_id", not(hasItem((int) otherCustomer))));
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
    void staffCanBePagedFilteredAndRoleUpdated() throws Exception {
        jdbc.update("DELETE FROM email_verification_tokens WHERE staff_id IN (SELECT staff_id FROM staff WHERE account = 'test.secretary')");
        jdbc.update("DELETE FROM password_reset_tokens WHERE staff_id IN (SELECT staff_id FROM staff WHERE account = 'test.secretary')");
        jdbc.update("DELETE FROM staff_profile_change_requests WHERE staff_id IN (SELECT staff_id FROM staff WHERE account = 'test.secretary')");
        jdbc.update("DELETE FROM staff WHERE account = 'test.secretary'");
        Integer branchStaffTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM staff WHERE branch_id = 1", Integer.class);
        int firstPageCount = Math.min(10, branchStaffTotal);
        int secondPageCount = Math.min(10, Math.max(0, branchStaffTotal - 10));

        mvc.perform(get("/api/staff")
                        .param("branchId", "1")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(firstPageCount)))
                .andExpect(jsonPath("$.totalElements", is(branchStaffTotal)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.pageSize", is(10)))
                .andExpect(jsonPath("$.content[0].branch_name").exists())
                .andExpect(jsonPath("$.content[0].role_name").exists());

        mvc.perform(get("/api/staff")
                        .param("branchId", "1")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(secondPageCount)))
                .andExpect(jsonPath("$.totalElements", is(branchStaffTotal)));

        mvc.perform(put("/api/staff/3")
                        .contentType("application/json")
                        .content("""
                                {
                                  "rolePermissionId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff_id", is(3)))
                .andExpect(jsonPath("$.email", is("staff@cms.test")))
                .andExpect(jsonPath("$.role_permission_id", is(2)))
                .andExpect(jsonPath("$.role_name", is("督導秘書")));
    }

    @Test
    void schemaMigrationAddsStaffEmailAndIndexToAnExistingStaffTable() {
        DriverManagerDataSource legacyDataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:staff_email_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(legacyDataSource);
        JdbcTemplate legacyJdbc = new JdbcTemplate(legacyDataSource);
        legacyJdbc.execute("DROP INDEX IF EXISTS idx_staff_email");
        legacyJdbc.execute("DROP TABLE IF EXISTS email_verification_tokens");
        legacyJdbc.execute("DROP TABLE IF EXISTS staff_profile_change_requests");
        legacyJdbc.execute("ALTER TABLE staff DROP COLUMN account_approved_at");
        legacyJdbc.execute("ALTER TABLE staff DROP COLUMN email_verified_at");
        legacyJdbc.execute("ALTER TABLE staff DROP COLUMN email");

        new SchemaMigrationRunner(legacyJdbc).run();

        Integer emailColumn = legacyJdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'staff' AND LOWER(COLUMN_NAME) = 'email'
                """, Integer.class);
        Integer emailIndex = legacyJdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
                WHERE LOWER(INDEX_NAME) = 'idx_staff_email'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, emailColumn);
        org.junit.jupiter.api.Assertions.assertEquals(1, emailIndex);
        Integer verificationColumn = legacyJdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'staff' AND LOWER(COLUMN_NAME) = 'email_verified_at'
                """, Integer.class);
        Integer verificationTable = legacyJdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE LOWER(TABLE_NAME) = 'email_verification_tokens'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, verificationColumn);
        org.junit.jupiter.api.Assertions.assertEquals(1, verificationTable);
        Integer approvalColumn = legacyJdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'staff' AND LOWER(COLUMN_NAME) = 'account_approved_at'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, approvalColumn);
        Integer profileChangeRequestsTable = legacyJdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE LOWER(TABLE_NAME) = 'staff_profile_change_requests'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, profileChangeRequestsTable);
    }

    @Test
    void staffEmailCannotBeMaintainedDirectly() throws Exception {
        mvc.perform(put("/api/staff/1")
                        .contentType("application/json")
                        .content("{\"rolePermissionId\":1,\"email\":\"manager@cms.test\"}"))
                .andExpect(status().isBadRequest());
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

    private long insertPasswordResetStaff(String account, String email, String password) {
        Long staffId = jdbc.queryForObject("SELECT COALESCE(MAX(staff_id), 0) + 1 FROM staff", Long.class);
        jdbc.update("""
                INSERT INTO staff (staff_id, role_permission_id, branch_id, staff_name, account, email, password_hash)
                VALUES (?, 3, 1, ?, ?, ?, ?)
                """, staffId, account, account, email, "{noop}" + password);
        return staffId;
    }

    private void insertPasswordResetToken(long staffId, String rawToken, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO password_reset_tokens (staff_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, staffId, sha256(rawToken), java.sql.Timestamp.from(expiresAt));
    }

    private void insertEmailVerificationToken(long staffId, String rawToken, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO email_verification_tokens (staff_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, staffId, sha256(rawToken), java.sql.Timestamp.from(expiresAt));
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

    private MockMultipartFile rentPaymentWorkbook(List<String[]> values) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("對帳資料");
            for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                String[] cells = values.get(rowIndex);
                for (int cellIndex = 0; cellIndex < cells.length; cellIndex++) {
                    row.createCell(cellIndex).setCellValue(cells[cellIndex]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "rent-payment-import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }
}
