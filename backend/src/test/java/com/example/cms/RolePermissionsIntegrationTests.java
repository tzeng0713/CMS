package com.example.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cms_permissions;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=always",
        "cms.seed.enabled=true"
})
@AutoConfigureMockMvc
class RolePermissionsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private MockHttpSession login(String account) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"" + account + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void reconciliationIsManagerWriteSupervisorReadAndGeneralDenied() throws Exception {
        mvc.perform(get("/api/rent-payments")).andExpect(status().isUnauthorized());
        MockHttpSession manager = login("manager");
        MockHttpSession supervisor = login("supervisor");
        MockHttpSession general = login("staff");

        mvc.perform(get("/api/rent-payments").session(manager)).andExpect(status().isOk());
        mvc.perform(get("/api/rent-payments").session(supervisor)).andExpect(status().isOk());
        mvc.perform(get("/api/rent-payments").session(general)).andExpect(status().isForbidden());
        mvc.perform(post("/api/rent-payments").session(supervisor).contentType("application/json")
                .content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/rent-payments").session(general).contentType("application/json")
                .content("{}" )).andExpect(status().isForbidden());
    }

    @Test
    void officeAndBranchEditsStayWithinStaffBranch() throws Exception {
        MockHttpSession general = login("staff"); // branch 2
        JsonNode offices = json.readTree(mvc.perform(get("/api/offices").session(general))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(offices.isArray());
        offices.forEach(office -> assertEquals(2, office.get("branch_id").asInt()));
        mvc.perform(put("/api/branches/1").session(general).contentType("application/json")
                .content("{}" )).andExpect(status().isForbidden());
        mvc.perform(put("/api/offices/1").session(general).contentType("application/json")
                .content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/branches").session(general).contentType("application/json")
                .content("{}" )).andExpect(status().isForbidden());
        JsonNode branches = json.readTree(mvc.perform(get("/api/branches").session(general))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(branches.size() >= 2);
        JsonNode targets = json.readTree(mvc.perform(get("/api/sales-targets").session(general))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        targets.get("content").forEach(target -> assertEquals(2, target.get("branch_id").asInt()));
    }

    @Test
    void firstPaymentInsideCustomerOnboardingRequiresManager() throws Exception {
        MockHttpSession general = login("staff");
        MockMultipartFile payload = new MockMultipartFile("payload", "", "application/json",
                "{\"customer\":{},\"contract\":{},\"firstPaymentAmount\":100}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/customers/with-contract").file(payload).session(general))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanBelongToBothBranchesButContractEditsUseContractBranch() throws Exception {
        MockHttpSession general = login("staff"); // branch 2
        MockHttpSession supervisor = login("supervisor"); // branch 1
        Long customerId = jdbc.queryForObject("""
                SELECT co.customer_id FROM contracts co JOIN offices o ON o.office_id = co.office_id
                WHERE o.branch_id = 1 AND NOT EXISTS (
                    SELECT 1 FROM contracts other_co JOIN offices other_o ON other_o.office_id = other_co.office_id
                    WHERE other_co.customer_id = co.customer_id AND other_o.branch_id = 2)
                LIMIT 1
                """, Long.class);
        Long branchOneContract = jdbc.queryForObject("""
                SELECT co.contract_id FROM contracts co JOIN offices o ON o.office_id = co.office_id
                WHERE co.customer_id = ? AND o.branch_id = 1 LIMIT 1
                """, Long.class, customerId);
        mvc.perform(get("/api/customers/{id}", customerId).session(general)).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers/{id}", customerId).session(supervisor)).andExpect(status().isOk());
        mvc.perform(put("/api/customers/{id}", customerId).session(supervisor).contentType("application/json")
                .content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/contracts/{id}", branchOneContract).session(general).contentType("application/json")
                .content("{}" )).andExpect(status().isForbidden());

        Long officeTwo = jdbc.queryForObject("SELECT office_id FROM offices WHERE branch_id = 2 LIMIT 1", Long.class);
        Long newId = jdbc.queryForObject("SELECT COALESCE(MAX(contract_id), 0) + 1 FROM contracts", Long.class);
        jdbc.update("INSERT INTO contracts (contract_id, customer_id, office_id, lease_status) VALUES (?, ?, ?, '綁約中')",
                newId, customerId, officeTwo);
        mvc.perform(get("/api/customers/{id}", customerId).session(general))
                .andExpect(status().isOk()).andExpect(jsonPath("$.can_edit").value(true));
        mvc.perform(put("/api/customers/{id}", customerId).session(general).contentType("application/json")
                .content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/contracts/{id}", branchOneContract).session(general).contentType("application/json")
                .content("{}" )).andExpect(status().isForbidden());
    }
}
