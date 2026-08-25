package com.example.cms.service;

import com.example.cms.dto.BonusRuleRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BonusRuleService extends CmsJdbcSupport {

    public static final Set<String> RULE_TYPES = Set.of(
            "OFFICE_RENTAL",
            "COMPANY_REGISTRATION",
            "TEAMWORK",
            "FULL_OCCUPANCY",
            "BUSINESS_AGENT",
            "REGISTRATION_MULTIPLIER",
            "BRANCH_PERFORMANCE",
            "ANNUAL_PAYMENT"
    );

    // 每種規則類型的結算引擎只會讀取「金額 / 比例 / 級距」三者之一，
    // 新增時強制只能填寫對應的那一欄，避免填了不會被用到的欄位造成誤解。
    private static final Set<String> UNIT_AMOUNT_RULE_TYPES = Set.of(
            "OFFICE_RENTAL", "COMPANY_REGISTRATION", "TEAMWORK", "FULL_OCCUPANCY", "BRANCH_PERFORMANCE", "BUSINESS_AGENT"
    );
    private static final Set<String> PERCENTAGE_RULE_TYPES = Set.of("ANNUAL_PAYMENT");
    private static final Set<String> TIER_CONFIG_RULE_TYPES = Set.of("REGISTRATION_MULTIPLIER");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BonusRuleService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT br.*, s1.staff_name AS created_by_name
                FROM bonus_rules br
                LEFT JOIN staff s1 ON s1.staff_id = br.created_by
                ORDER BY br.bonus_rule_id
                """);
    }

    public Map<String, Object> create(BonusRuleRequest request) {
        requireManager(request.staffId());
        Validated v = validate(request);

        Long id = nextId("bonus_rules", "bonus_rule_id");
        jdbc.update("""
                INSERT INTO bonus_rules (
                    bonus_rule_id, rule_name, rule_type, unit_amount, percentage, tier_config,
                    period_type, description, is_active, created_by, created_at, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                """,
                id, v.ruleName(), v.ruleType(), v.unitAmount(), v.percentage(), v.tierConfig(),
                v.periodType(), v.description(), v.isActive(), request.staffId(), request.staffId());

        return jdbc.queryForMap("SELECT * FROM bonus_rules WHERE bonus_rule_id = ?", id);
    }

    public Map<String, Object> update(long id, BonusRuleRequest request) {
        requireManager(request.staffId());
        requireExisting(id);
        Validated v = validate(request);

        jdbc.update("""
                UPDATE bonus_rules
                SET rule_name = ?, rule_type = ?, unit_amount = ?, percentage = ?, tier_config = ?,
                    period_type = ?, description = ?, is_active = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE bonus_rule_id = ?
                """,
                v.ruleName(), v.ruleType(), v.unitAmount(), v.percentage(), v.tierConfig(),
                v.periodType(), v.description(), v.isActive(), request.staffId(), id);

        return jdbc.queryForMap("SELECT * FROM bonus_rules WHERE bonus_rule_id = ?", id);
    }

    private record Validated(
            String ruleName, String ruleType, BigDecimal unitAmount, BigDecimal percentage,
            String tierConfig, String periodType, String description, boolean isActive
    ) {
    }

    private Validated validate(BonusRuleRequest request) {
        String ruleName = blankToNull(request.ruleName());
        if (ruleName == null) {
            throw new IllegalArgumentException("ruleName is required");
        }
        String ruleType = blankToNull(request.ruleType());
        if (ruleType == null || !RULE_TYPES.contains(ruleType)) {
            throw new IllegalArgumentException("ruleType must be one of " + RULE_TYPES);
        }
        BigDecimal unitAmount = request.unitAmount();
        if (unitAmount != null && unitAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("unitAmount must not be negative");
        }
        BigDecimal percentage = request.percentage();
        if (percentage != null && percentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("percentage must not be negative");
        }
        String tierConfig = blankToNull(request.tierConfig());

        validateFieldMatchesRuleType(ruleType, unitAmount, percentage, tierConfig);
        if (tierConfig != null) {
            validateTierConfig(tierConfig);
        }

        boolean isActive = request.isActive() == null || request.isActive();
        return new Validated(ruleName, ruleType, unitAmount, percentage, tierConfig,
                blankToNull(request.periodType()), blankToNull(request.description()), isActive);
    }

    private void requireExisting(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM bonus_rules WHERE bonus_rule_id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "bonus rule not found");
        }
    }

    // 依 ruleType 決定「金額 / 比例 / 級距」三者中唯一該填的欄位，強制只能填那一欄，
    // 避免填了不會被結算引擎讀取的欄位（見 PerformanceBonusService 各規則的計算邏輯）。
    private void validateFieldMatchesRuleType(String ruleType, BigDecimal unitAmount, BigDecimal percentage, String tierConfig) {
        if (UNIT_AMOUNT_RULE_TYPES.contains(ruleType)) {
            if (unitAmount == null) {
                throw new IllegalArgumentException("ruleType=" + ruleType + " 需要填寫 unitAmount");
            }
            if (percentage != null || tierConfig != null) {
                throw new IllegalArgumentException("ruleType=" + ruleType + " 只需要 unitAmount，請勿同時填寫 percentage 或 tierConfig");
            }
        } else if (PERCENTAGE_RULE_TYPES.contains(ruleType)) {
            if (percentage == null) {
                throw new IllegalArgumentException("ruleType=" + ruleType + " 需要填寫 percentage");
            }
            if (unitAmount != null || tierConfig != null) {
                throw new IllegalArgumentException("ruleType=" + ruleType + " 只需要 percentage，請勿同時填寫 unitAmount 或 tierConfig");
            }
        } else if (TIER_CONFIG_RULE_TYPES.contains(ruleType)) {
            if (tierConfig == null) {
                throw new IllegalArgumentException("ruleType=" + ruleType + " 需要填寫 tierConfig");
            }
            if (unitAmount != null || percentage != null) {
                throw new IllegalArgumentException("ruleType=" + ruleType + " 只需要 tierConfig，請勿同時填寫 unitAmount 或 percentage");
            }
        }
    }

    private void validateTierConfig(String tierConfig) {
        List<Map<String, Object>> tiers;
        try {
            tiers = objectMapper.readValue(tierConfig,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("tierConfig 必須是合法 JSON，格式如 [{\"threshold\":3,\"amount\":2000}]");
        }
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("tierConfig 至少需要一個級距");
        }
        for (Map<String, Object> tier : tiers) {
            if (!(tier.get("threshold") instanceof Number) || !(tier.get("amount") instanceof Number)) {
                throw new IllegalArgumentException("tierConfig 每個級距都需要數字型別的 threshold 與 amount");
            }
        }
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only 主管 can manage bonus rules");
        }
    }
}
