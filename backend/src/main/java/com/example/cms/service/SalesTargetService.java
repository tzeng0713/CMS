package com.example.cms.service;

import com.example.cms.dto.SalesTargetEntry;
import com.example.cms.dto.SalesTargetRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SalesTargetService extends CmsJdbcSupport {

    private static final List<String> VALID_CATEGORIES = List.of("辦公室", "座位", "登記", "聯絡處", "停業");

    @Autowired(required = false)
    private RoleAccess access;

    public SalesTargetService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> list(Long staffId, Integer targetMonth, String category, Integer page, Integer pageSize) {
        if (access != null && !access.actor().manager()) staffId = access.actor().staffId();
        String categoryText = blankToNull(category);

        String where = """
                 WHERE (? IS NULL OR st.staff_id = ?)
                   AND (? IS NULL OR st.target_month = ?)
                   AND (? IS NULL OR st.category = ?)
                """;
        List<Object> args = new ArrayList<>();
        args.add(staffId); args.add(staffId);
        args.add(targetMonth); args.add(targetMonth);
        args.add(categoryText); args.add(categoryText);

        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;

        List<Object> selectArgs = new ArrayList<>(args);
        selectArgs.add(size);
        selectArgs.add(pageNumber * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT st.*, s.staff_name, b.branch_name, s1.staff_name AS created_by_name
                FROM sales_targets st
                JOIN staff s ON s.staff_id = st.staff_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                LEFT JOIN staff s1 ON s1.staff_id = st.created_by
                """ + where + " ORDER BY st.target_month DESC, st.category LIMIT ? OFFSET ?",
                selectArgs.toArray());

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales_targets st" + where, Long.class, args.toArray());

        for (Map<String, Object> row : rows) {
            Long rowStaffId = ((Number) row.get("staff_id")).longValue();
            Integer rowMonth = ((Number) row.get("target_month")).intValue();
            String rowCategory = (String) row.get("category");
            int actualCount = actualCount(rowStaffId, rowMonth, rowCategory);
            row.put("actual_count", actualCount);
            Object targetCountObj = row.get("target_count");
            if (targetCountObj != null && ((Number) targetCountObj).intValue() > 0) {
                BigDecimal rate = BigDecimal.valueOf(actualCount)
                        .divide(BigDecimal.valueOf(((Number) targetCountObj).intValue()), 4, RoundingMode.HALF_UP);
                row.put("achievement_rate", rate);
            } else {
                row.put("achievement_rate", null);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    /**
     * 一次建立多筆業績目標，同一個秘書、同一個月份，但可以同時新增不同類別各自的目標數
     * （例如林雅婷這個月：辦公室 3、登記 1）。整批驗證過才整批寫入，任何一筆有問題就全部不寫入。
     */
    @Transactional
    public List<Map<String, Object>> create(SalesTargetRequest request) {
        requireManager(request.staffId());

        Long targetStaffId = requiredId(request.targetStaffId(), "targetStaffId");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE staff_id = ?", Integer.class, targetStaffId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("targetStaffId not found");
        }
        Integer targetMonth = request.targetMonth();
        if (targetMonth == null || String.valueOf(targetMonth).length() != 6) {
            throw new IllegalArgumentException("targetMonth must be in YYYYMM format");
        }
        List<SalesTargetEntry> entries = request.entries();
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }

        Set<String> seenCategories = new HashSet<>();
        for (SalesTargetEntry entry : entries) {
            String category = blankToNull(entry.category());
            if (category == null || !VALID_CATEGORIES.contains(category)) {
                throw new IllegalArgumentException("category must be one of " + VALID_CATEGORIES);
            }
            if (!seenCategories.add(category)) {
                throw new IllegalArgumentException("同一批次不能重複新增同一個類別：" + category);
            }
            if (entry.targetCount() == null || entry.targetCount() < 0) {
                throw new IllegalArgumentException("targetCount must not be negative");
            }
            Integer duplicate = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sales_targets
                    WHERE staff_id = ? AND target_month = ? AND category = ?
                    """, Integer.class, targetStaffId, targetMonth, category);
            if (duplicate != null && duplicate > 0) {
                throw new IllegalArgumentException("此秘書/月份/類別的業績目標已存在：" + category);
            }
        }

        List<Map<String, Object>> created = new ArrayList<>();
        for (SalesTargetEntry entry : entries) {
            String category = entry.category().trim();
            Long id = nextId("sales_targets", "sales_target_id");
            jdbc.update("""
                    INSERT INTO sales_targets (sales_target_id, staff_id, target_month, category, target_count, created_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, id, targetStaffId, targetMonth, category, entry.targetCount(), request.staffId());
            created.add(jdbc.queryForMap("""
                    SELECT st.*, s.staff_name, b.branch_name
                    FROM sales_targets st
                    JOIN staff s ON s.staff_id = st.staff_id
                    LEFT JOIN branches b ON b.branch_id = s.branch_id
                    WHERE st.sales_target_id = ?
                    """, id));
        }
        return created;
    }

    /**
     * 實際達成數：該秘書、該月份，rental_item 符合這個業績目標類別、簽約起始日落在目標月份內的合約數。
     * 只算簽約人（signer_staff_id），雙秘書合作簽約時算在簽約秘書上，不算協辦秘書（partner_staff_id）。
     */
    private int actualCount(Long staffId, int targetMonth, String category) {
        String monthPrefix = String.format("%04d-%02d", targetMonth / 100, targetMonth % 100);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT c.contract_id)
                FROM contracts c
                WHERE c.rental_item = ?
                  AND c.signer_staff_id = ?
                  AND c.start_date_text LIKE ?
                """, Integer.class, category, staffId, monthPrefix + "%");
        return count == null ? 0 : count;
    }

    private void requireManager(Long staffId) {
        requiredId(staffId, "staffId");
        String roleName;
        try {
            roleName = jdbc.queryForObject("""
                    SELECT rp.role_name FROM staff s
                    JOIN role_permissions rp ON rp.role_permission_id = s.role_permission_id
                    WHERE s.staff_id = ?
                    """, String.class, staffId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "staff not found");
        }
        if (!"主管".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only 主管 can create sales targets");
        }
    }
}
