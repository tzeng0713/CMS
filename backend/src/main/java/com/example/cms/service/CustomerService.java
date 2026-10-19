package com.example.cms.service;

import com.example.cms.dto.CustomerRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CustomerService extends CmsJdbcSupport {
    @Autowired(required = false)
    private RoleAccess access;

    public CustomerService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> customers(String search, String ownerName, String companyName, String taxId,
                                         String phone, String accountInfo, Long branchId, String officeNo,
                                         Integer ownerBirthdayMonth, Integer contactBirthdayMonth,
                                         Integer page, Integer pageSize) {
        validateBirthdayMonth(ownerBirthdayMonth);
        validateBirthdayMonth(contactBirthdayMonth);
        String searchText = search == null ? "" : search.trim();

        boolean hasDetailedFilters = hasCustomerFilters(companyName, taxId, phone, accountInfo, ownerName,
                branchId, officeNo, ownerBirthdayMonth, contactBirthdayMonth);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (access != null && access.actor().generalSecretary()) {
            where.append("""
                    AND EXISTS (SELECT 1 FROM contracts scoped_contract
                                JOIN offices scoped_office ON scoped_office.office_id = scoped_contract.office_id
                                WHERE scoped_contract.customer_id = c.customer_id AND scoped_office.branch_id = ?)
                    """);
            args.add(access.actor().branchId());
        }
        addLikeFilter(where, args, "c.company_name", companyName);
        addLikeFilter(where, args, "c.tax_id", taxId);
        addLikeFilter(where, args, "c.phone", phone);
        addLikeFilter(where, args, "c.account_info", accountInfo);
        addLikeFilter(where, args, "c.owner_name", ownerName);

        String officeText = blankToEmpty(officeNo);
        if (branchId != null || !officeText.isBlank()) {
            where.append("""
                    AND EXISTS (
                        SELECT 1
                        FROM contracts filtered_contract
                        LEFT JOIN offices filtered_office ON filtered_office.office_id = filtered_contract.office_id
                        WHERE filtered_contract.customer_id = c.customer_id
                    """);
            if (branchId != null) {
                where.append(" AND filtered_office.branch_id = ?");
                args.add(branchId);
            }
            addLikeFilter(where, args, "filtered_office.office_no", officeNo);
            where.append(")");
        }
        addBirthdayMonthFilter(where, args, "c.owner_birthday", ownerBirthdayMonth);
        addBirthdayMonthFilter(where, args, "c.contact_birthday", contactBirthdayMonth);

        if (!hasDetailedFilters && !searchText.isBlank()) {
            String like = "%" + searchText + "%";
            where.append("""
                    AND (
                        c.company_name LIKE ?
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
                    )
                    """);
            for (int i = 0; i < 6; i++) {
                args.add(like);
            }
        }

        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;
        long offset = (long) pageNumber * size;
        String orderBy = !hasDetailedFilters && !searchText.isBlank()
                ? " ORDER BY c.owner_name, c.customer_id DESC"
                : " ORDER BY c.customer_id DESC";

        List<Object> selectArgs = new ArrayList<>(args);
        selectArgs.add(size);
        selectArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                customerListSql() + where + orderBy + " LIMIT ? OFFSET ?", selectArgs.toArray());
        rows.forEach(this::addEditPermission);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customers c" + where, Long.class, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    public Map<String, Object> customerDetail(long id) {
        Map<String, Object> detail = jdbc.queryForMap(customerListSql() + " WHERE c.customer_id = ?", id);
        addEditPermission(detail);
        String ownerName = (String) detail.get("owner_name");
        Long ownBranch = access != null && access.actor().generalSecretary() ? access.actor().branchId() : null;
        detail.put("contracts", jdbc.queryForList("""
                SELECT co.*, o.office_no, b.branch_name, s.staff_name AS signer_staff_name,
                       partner.staff_name AS partner_staff_name
                FROM contracts co
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                LEFT JOIN staff s ON s.staff_id = co.signer_staff_id
                LEFT JOIN staff partner ON partner.staff_id = co.partner_staff_id
                WHERE co.customer_id = ? AND (? IS NULL OR o.branch_id = ?)
                ORDER BY co.contract_id DESC
                """, id, ownBranch, ownBranch));
        detail.put("relatedCompanies", relatedCompanies(id));
        detail.put("relationGroup", relationGroupForCustomer(id));
        detail.put("sameOwnerCompanies", sameOwnerCompanies(ownerName, id));
        detail.put("rentPayments", ownBranch != null ? List.of() : jdbc.queryForList("""
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
        Long ownBranch = access != null && access.actor().generalSecretary() ? access.actor().branchId() : null;
        return jdbc.queryForList("""
                SELECT c.customer_id, c.company_name, c.tax_id, c.status, c.owner_name,
                       c.rental_item, c.rental_status, c.owner_birthday,
                       c.contact_person, c.contact_birthday, c.phone, c.registration_type,
                       c.referrer, c.accountant_info, c.account_info, c.is_agent,
                       co.contract_id, co.lease_status, co.rent, co.deposit,
                       o.office_no, o.branch_id, b.branch_name
                FROM customers c
                LEFT JOIN contracts co ON co.customer_id = c.customer_id
                  AND co.contract_id = (SELECT MAX(latest.contract_id) FROM contracts latest WHERE latest.customer_id = c.customer_id)
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                WHERE (? = '%%' OR c.company_name LIKE ?)
                  AND (? IS NULL OR EXISTS (SELECT 1 FROM contracts scoped_contract
                        JOIN offices scoped_office ON scoped_office.office_id = scoped_contract.office_id
                        WHERE scoped_contract.customer_id = c.customer_id AND scoped_office.branch_id = ?))
                ORDER BY c.company_name
                LIMIT 20
                """, like, like, ownBranch, ownBranch);
    }

    /**
     * Finds existing relation groups through any member name. This is an explicit
     * "join a group" lookup; it never infers a group from a tax ID.
     */
    public List<Map<String, Object>> relationGroupLookup(String search) {
        String searchText = blankToNull(search);
        if (searchText == null) {
            return List.of();
        }
        List<Long> groupIds = jdbc.queryForList("""
                SELECT DISTINCT relation_group_id
                FROM customer_relation_members
                WHERE LOWER(company_name) LIKE LOWER(?)
                ORDER BY relation_group_id DESC
                LIMIT 12
                """, Long.class, "%" + searchText + "%");
        return relationGroupSummaries(groupIds);
    }

    /**
     * A name may have been entered by a colleague before that company has a
     * customer record. Return the matching group so the UI can ask the user to
     * join it and display the shared note as read-only.
     */
    public List<Map<String, Object>> pendingRelationGroupMatches(String companyName) {
        String normalizedName = blankToNull(companyName);
        if (normalizedName == null) {
            return List.of();
        }
        List<Long> groupIds = jdbc.queryForList("""
                SELECT DISTINCT relation_group_id
                FROM customer_relation_members
                WHERE customer_id IS NULL
                  AND LOWER(company_name) = LOWER(?)
                ORDER BY relation_group_id DESC
                """, Long.class, normalizedName);
        return relationGroupSummaries(groupIds);
    }

    @Transactional
    public Map<String, Object> createCustomer(CustomerRequest request) {
        String companyName = requiredCompanyName(request.companyName());
        requireUniqueCompanyName(companyName, null);
        Long id = nextId("customers", "customer_id");
        String accountantInfo = blankToNull(request.accountantInfo());
        if (accountantInfo == null) {
            accountantInfo = blankToNull(request.referrer());
        }
        jdbc.update("""
                INSERT INTO customers (
                    customer_id, company_name, tax_id, status, rental_item, rental_status,
                    owner_name, owner_birthday, contact_person, contact_birthday, phone, forwarding_address,
                    petty_cash, referrer, accountant_info, account_info, is_agent,
                    notes, registration_type, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                id,
                companyName,
                blankToNull(request.taxId()),
                normalizeStatus(request.status()),
                normalizeRentalItem(request.rentalItem(), request.registrationType()),
                normalizeRentalStatus(request.rentalStatus()),
                blankToNull(request.ownerName()),
                blankToNull(request.ownerBirthday()),
                blankToNull(request.contactPerson()),
                blankToNull(request.contactBirthday()),
                blankToNull(request.phone()),
                blankToNull(request.forwardingAddress()),
                request.pettyCash(),
                accountantInfo,
                accountantInfo,
                blankToNull(request.accountInfo()),
                Boolean.TRUE.equals(request.isAgent()),
                blankToNull(request.notes()),
                normalizeRegistrationType(request.registrationType(), request.rentalItem()),
                request.updatedBy() == null ? 1L : request.updatedBy());
        linkPendingRelationMember(id, companyName);
        saveRelatedCompanies(id, companyName, request.relatedCompanyNames(),
                request.relationGroupId(), request.relationGroupNote());
        return customerDetail(id);
    }

    public Map<String, Object> updateCustomer(long id, CustomerRequest request) {
        String companyName = requiredCompanyName(request.companyName());
        requireUniqueCompanyName(companyName, id);
        String accountantInfo = blankToNull(request.accountantInfo());
        if (accountantInfo == null) {
            accountantInfo = blankToNull(request.referrer());
        }
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
                    contact_birthday = ?,
                    phone = ?,
                    forwarding_address = ?,
                    petty_cash = ?,
                    referrer = ?,
                    accountant_info = ?,
                    account_info = ?,
                    is_agent = ?,
                    notes = ?,
                    registration_type = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE customer_id = ?
                """,
                companyName,
                blankToNull(request.taxId()),
                normalizeStatus(request.status()),
                normalizeRentalItem(request.rentalItem(), request.registrationType()),
                normalizeRentalStatus(request.rentalStatus()),
                blankToNull(request.ownerName()),
                blankToNull(request.ownerBirthday()),
                blankToNull(request.contactPerson()),
                blankToNull(request.contactBirthday()),
                blankToNull(request.phone()),
                blankToNull(request.forwardingAddress()),
                request.pettyCash(),
                accountantInfo,
                accountantInfo,
                blankToNull(request.accountInfo()),
                Boolean.TRUE.equals(request.isAgent()),
                blankToNull(request.notes()),
                normalizeRegistrationType(request.registrationType(), request.rentalItem()),
                request.updatedBy() == null ? 1L : request.updatedBy(),
                id);
        saveRelatedCompanies(id, companyName, request.relatedCompanyNames(),
                request.relationGroupId(), request.relationGroupNote());
        return customerDetail(id);
    }

    public List<Map<String, Object>> sameOwnerCompanies(String ownerName, long excludeCustomerId) {
        if (ownerName == null || ownerName.isBlank()) {
            return List.of();
        }
        Long ownBranch = access != null && access.actor().generalSecretary() ? access.actor().branchId() : null;
        return jdbc.queryForList("""
                SELECT c.customer_id, c.company_name, c.owner_name, c.phone, c.status,
                       c.rental_item, c.rental_status, c.registration_type
                FROM customers c
                WHERE c.owner_name = ? AND c.customer_id <> ?
                  AND (? IS NULL OR EXISTS (SELECT 1 FROM contracts co JOIN offices o ON o.office_id = co.office_id
                       WHERE co.customer_id = c.customer_id AND o.branch_id = ?))
                ORDER BY c.company_name
                """, ownerName, excludeCustomerId, ownBranch, ownBranch);
    }

    String requiredCompanyName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("companyName is required");
        }
        return value.trim();
    }

    private void requireUniqueCompanyName(String companyName, Long excludedCustomerId) {
        String sql = "SELECT COUNT(*) FROM customers WHERE LOWER(TRIM(company_name)) = LOWER(?)";
        Integer duplicateCount;
        if (excludedCustomerId == null) {
            duplicateCount = jdbc.queryForObject(sql, Integer.class, companyName);
        } else {
            duplicateCount = jdbc.queryForObject(sql + " AND customer_id <> ?", Integer.class, companyName, excludedCustomerId);
        }
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalArgumentException("companyName already exists");
        }
    }

    private String customerListSql() {
        return """
                SELECT c.*,
                       co.contract_id, co.lease_status, co.rent, co.deposit,
                       co.signed_date_text, co.end_date_text,
                       o.office_no, o.branch_id, b.branch_name
                FROM customers c
                LEFT JOIN contracts co ON co.customer_id = c.customer_id
                  AND co.contract_id = (SELECT MAX(latest.contract_id) FROM contracts latest WHERE latest.customer_id = c.customer_id)
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                """;
    }

    private void addEditPermission(Map<String, Object> row) {
        if (access == null || access.actor().manager()) {
            row.put("can_edit", true);
            return;
        }
        Long customerId = ((Number) row.get("customer_id")).longValue();
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM contracts co JOIN offices o ON o.office_id = co.office_id
                WHERE co.customer_id = ? AND o.branch_id = ?
                """, Integer.class, customerId, access.actor().branchId());
        row.put("can_edit", count != null && count > 0);
    }

    private void addLikeFilter(StringBuilder where, List<Object> args, String column, String value) {
        String text = blankToEmpty(value);
        if (!text.isBlank()) {
            where.append(" AND ").append(column).append(" LIKE ?");
            args.add("%" + text + "%");
        }
    }

    private void addBirthdayMonthFilter(StringBuilder where, List<Object> args, String column, Integer month) {
        if (month != null) {
            where.append(" AND ").append(column).append(" REGEXP ?");
            args.add("(^|[-/.])0?" + month + "([-/.])");
        }
    }

    private boolean hasCustomerFilters(String companyName, String taxId, String phone, String accountInfo,
                                       String ownerName,
                                       Long branchId, String officeNo, Integer ownerBirthdayMonth,
                                       Integer contactBirthdayMonth) {
        return !blankToEmpty(companyName).isBlank()
                || !blankToEmpty(taxId).isBlank()
                || !blankToEmpty(phone).isBlank()
                || !blankToEmpty(accountInfo).isBlank()
                || !blankToEmpty(ownerName).isBlank()
                || branchId != null
                || !blankToEmpty(officeNo).isBlank()
                || ownerBirthdayMonth != null
                || contactBirthdayMonth != null;
    }

    private void validateBirthdayMonth(Integer month) {
        if (month != null && (month < 1 || month > 12)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "birthday month must be between 1 and 12");
        }
    }

    Integer normalizeStatus(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0 || value > 2) {
            throw new IllegalArgumentException("status must be 0, 1, or 2");
        }
        return value;
    }

    String normalizeRentalItem(String value, String fallback) {
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
        if (!List.of("辦公室", "座位", "登記", "聯絡處", "停業").contains(item)) {
            throw new IllegalArgumentException("rentalItem must be 辦公室, 座位, 登記, 聯絡處, or 停業");
        }
        return item;
    }

    Integer normalizeRentalStatus(Integer value) {
        if (value == null) {
            return 1;
        }
        if (value < 1 || value > 4) {
            throw new IllegalArgumentException("rentalStatus must be 1, 2, 3, or 4");
        }
        return value;
    }

    String normalizeRegistrationType(String value, String fallback) {
        String text = blankToNull(value);
        if (text != null) {
            return "實體辦公室".equals(text) ? "辦公室" : text;
        }
        String item = blankToNull(fallback);
        return item == null ? null : ("實體辦公室".equals(item) ? "辦公室" : item);
    }

    private List<Map<String, Object>> relatedCompanies(long customerId) {
        Long groupId = relationGroupIdForCustomer(customerId);
        if (groupId == null) {
            return List.of();
        }
        Long ownBranch = access != null && access.actor().generalSecretary() ? access.actor().branchId() : null;
        return jdbc.queryForList("""
                SELECT relation_member_id, customer_id, company_name,
                       CASE WHEN customer_id IS NULL THEN FALSE ELSE TRUE END AS is_resolved
                FROM customer_relation_members crm
                WHERE relation_group_id = ?
                  AND (customer_id IS NULL OR customer_id <> ?)
                  AND (? IS NULL OR EXISTS (SELECT 1 FROM contracts co JOIN offices o ON o.office_id = co.office_id
                       WHERE co.customer_id = crm.customer_id AND o.branch_id = ?))
                ORDER BY company_name
                """, groupId, customerId, ownBranch, ownBranch);
    }

    private Map<String, Object> relationGroupForCustomer(long customerId) {
        Long groupId = relationGroupIdForCustomer(customerId);
        return groupId == null ? null : relationGroupSummary(groupId);
    }

    private List<Map<String, Object>> relationGroupSummaries(List<Long> groupIds) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Long groupId : groupIds) {
            Map<String, Object> group = relationGroupSummary(groupId);
            if (group != null) {
                groups.add(group);
            }
        }
        return groups;
    }

    private Map<String, Object> relationGroupSummary(long groupId) {
        Long ownBranch = access != null && access.actor().generalSecretary() ? access.actor().branchId() : null;
        List<Map<String, Object>> groups = jdbc.queryForList("""
                SELECT relation_group_id, group_note
                FROM customer_relation_groups
                WHERE relation_group_id = ?
                """, groupId);
        if (groups.isEmpty()) {
            return null;
        }
        Map<String, Object> group = new LinkedHashMap<>(groups.get(0));
        List<Map<String, Object>> members = jdbc.queryForList("""
                SELECT relation_member_id, customer_id, company_name,
                       CASE WHEN customer_id IS NULL THEN FALSE ELSE TRUE END AS is_resolved
                FROM customer_relation_members crm
                WHERE relation_group_id = ?
                  AND (? IS NULL OR EXISTS (SELECT 1 FROM contracts co JOIN offices o ON o.office_id = co.office_id
                       WHERE co.customer_id = crm.customer_id AND o.branch_id = ?))
                ORDER BY company_name
                """, groupId, ownBranch, ownBranch);
        if (ownBranch != null && members.isEmpty()) return null;
        group.put("members", members);
        group.put("member_count", members.size());
        return group;
    }

    private void linkPendingRelationMember(long customerId, String companyName) {
        List<Long> pendingMembers = jdbc.queryForList("""
                SELECT relation_member_id
                FROM customer_relation_members
                WHERE customer_id IS NULL
                  AND LOWER(company_name) = LOWER(?)
                ORDER BY relation_member_id
                """, Long.class, companyName);
        if (pendingMembers.size() == 1) {
            jdbc.update("""
                    UPDATE customer_relation_members
                    SET customer_id = ?, company_name = ?
                    WHERE relation_member_id = ?
                    """, customerId, companyName, pendingMembers.get(0));
        }
    }

    private void saveRelatedCompanies(long customerId, String companyName, List<String> requestedNames,
                                      Long requestedGroupId, String requestedGroupNote) {
        List<String> names = requestedNames == null
                ? List.of()
                : normalizedRelatedCompanyNames(companyName, requestedNames);
        Long groupId = relationGroupIdForCustomer(customerId);

        if (requestedGroupId != null) {
            if (relationGroupSummary(requestedGroupId) == null) {
                throw new IllegalArgumentException("relationGroupId does not exist");
            }
            if (groupId != null && !requestedGroupId.equals(groupId)) {
                mergeRelationGroups(requestedGroupId, groupId);
            }
            groupId = requestedGroupId;
        }

        if (groupId == null && !names.isEmpty()) {
            for (String name : names) {
                Long relatedCustomerId = uniqueCustomerIdByCompanyName(name);
                if (relatedCustomerId != null) {
                    groupId = relationGroupIdForCustomer(relatedCustomerId);
                    if (groupId != null) {
                        break;
                    }
                }
            }
        }

        if (groupId == null && !names.isEmpty()) {
            groupId = nextId("customer_relation_groups", "relation_group_id");
            jdbc.update("INSERT INTO customer_relation_groups (relation_group_id, group_note) VALUES (?, ?)",
                    groupId, blankToNull(requestedGroupNote));
        }
        if (groupId == null) {
            return;
        }

        ensureRelationMember(groupId, customerId, companyName);
        for (String name : names) {
            Long relatedCustomerId = uniqueCustomerIdByCompanyName(name);
            if (relatedCustomerId != null) {
                Long relatedGroupId = relationGroupIdForCustomer(relatedCustomerId);
                if (relatedGroupId != null && !groupId.equals(relatedGroupId)) {
                    mergeRelationGroups(groupId, relatedGroupId);
                }
            }
            ensureRelationMember(groupId, relatedCustomerId, name);
        }
        if (requestedGroupNote != null) {
            jdbc.update("UPDATE customer_relation_groups SET group_note = ? WHERE relation_group_id = ?",
                    blankToNull(requestedGroupNote), groupId);
        }
    }

    private List<String> normalizedRelatedCompanyNames(String companyName, List<String> requestedNames) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        seen.add(companyName.trim().toLowerCase());
        for (String value : requestedNames) {
            String name = blankToNull(value);
            if (name == null) {
                continue;
            }
            String key = name.toLowerCase();
            if (seen.add(key)) {
                result.add(name);
            }
        }
        return result;
    }

    private Long relationGroupIdForCustomer(long customerId) {
        List<Long> groups = jdbc.queryForList("""
                SELECT relation_group_id
                FROM customer_relation_members
                WHERE customer_id = ?
                """, Long.class, customerId);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private Long uniqueCustomerIdByCompanyName(String companyName) {
        List<Long> ids = jdbc.queryForList("""
                SELECT customer_id
                FROM customers
                WHERE LOWER(company_name) = LOWER(?)
                ORDER BY customer_id
                """, Long.class, companyName);
        return ids.size() == 1 ? ids.get(0) : null;
    }

    private void ensureRelationMember(long groupId, Long customerId, String companyName) {
        List<Map<String, Object>> sameName = jdbc.queryForList("""
                SELECT relation_member_id, customer_id
                FROM customer_relation_members
                WHERE relation_group_id = ?
                  AND LOWER(company_name) = LOWER(?)
                """, groupId, companyName);
        if (!sameName.isEmpty()) {
            Object linkedCustomer = sameName.get(0).get("customer_id");
            if (linkedCustomer == null && customerId != null) {
                jdbc.update("UPDATE customer_relation_members SET customer_id = ?, company_name = ? WHERE relation_member_id = ?",
                        customerId, companyName, sameName.get(0).get("relation_member_id"));
            }
            return;
        }
        if (customerId != null && relationGroupIdForCustomer(customerId) != null) {
            return;
        }
        Long memberId = nextId("customer_relation_members", "relation_member_id");
        jdbc.update("""
                INSERT INTO customer_relation_members (
                    relation_member_id, relation_group_id, customer_id, company_name
                ) VALUES (?, ?, ?, ?)
                """, memberId, groupId, customerId, companyName);
    }

    private void mergeRelationGroups(long targetGroupId, long sourceGroupId) {
        String targetNote = jdbc.queryForObject(
                "SELECT group_note FROM customer_relation_groups WHERE relation_group_id = ?",
                String.class, targetGroupId);
        String sourceNote = jdbc.queryForObject(
                "SELECT group_note FROM customer_relation_groups WHERE relation_group_id = ?",
                String.class, sourceGroupId);
        if (blankToNull(targetNote) == null && blankToNull(sourceNote) != null) {
            jdbc.update("UPDATE customer_relation_groups SET group_note = ? WHERE relation_group_id = ?",
                    sourceNote, targetGroupId);
        }
        List<Map<String, Object>> sourceMembers = jdbc.queryForList("""
                SELECT relation_member_id, customer_id, company_name
                FROM customer_relation_members
                WHERE relation_group_id = ?
                ORDER BY relation_member_id
                """, sourceGroupId);
        for (Map<String, Object> source : sourceMembers) {
            String companyName = (String) source.get("company_name");
            List<Map<String, Object>> targetMembers = jdbc.queryForList("""
                    SELECT relation_member_id, customer_id
                    FROM customer_relation_members
                    WHERE relation_group_id = ?
                      AND LOWER(company_name) = LOWER(?)
                    """, targetGroupId, companyName);
            if (targetMembers.isEmpty()) {
                jdbc.update("UPDATE customer_relation_members SET relation_group_id = ? WHERE relation_member_id = ?",
                        targetGroupId, source.get("relation_member_id"));
                continue;
            }
            Object targetCustomerId = targetMembers.get(0).get("customer_id");
            Object sourceCustomerId = source.get("customer_id");
            if (targetCustomerId == null && sourceCustomerId != null) {
                jdbc.update("UPDATE customer_relation_members SET customer_id = ? WHERE relation_member_id = ?",
                        sourceCustomerId, targetMembers.get(0).get("relation_member_id"));
            }
            jdbc.update("DELETE FROM customer_relation_members WHERE relation_member_id = ?",
                    source.get("relation_member_id"));
        }
        jdbc.update("DELETE FROM customer_relation_groups WHERE relation_group_id = ?", sourceGroupId);
    }
}
