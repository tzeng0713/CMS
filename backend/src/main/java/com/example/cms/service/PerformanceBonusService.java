package com.example.cms.service;

import com.example.cms.dto.ManualPerformanceBonusRequest;
import com.example.cms.dto.SettleMonthlyBonusRequest;
import com.example.cms.dto.SettlePeriodBonusRequest;
import com.example.cms.dto.SyncTransactionBonusRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PerformanceBonusService extends CmsJdbcSupport {

    private static final Pattern PERIOD_PATTERN = Pattern.compile("^(\\d{4})-P([123])$");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})$");

    // 沒有自動結算引擎的規則類型：只有這些規則能透過 manualCreate() 手動登打，
    // 避免主管誤對已有自動結算流程（sync-transactions／settle-monthly／settle-period）的規則手動入帳造成重複發放。
    private static final Set<String> MANUALLY_ONLY_RULE_TYPES = Set.of("BUSINESS_AGENT");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerformanceBonusService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    // ---------- 查詢業績結算資料 ----------

    public Map<String, Object> list(String ruleType, String excludeRuleType, String period, Long branchId, Long staffId, Long contractId,
                                     Integer page, Integer pageSize) {
        String ruleTypeText = blankToNull(ruleType);
        String excludeRuleTypeText = blankToNull(excludeRuleType);
        String periodText = blankToNull(period);

        String where = """
                 WHERE (? IS NULL OR pb.rule_type = ?)
                   AND (? IS NULL OR pb.rule_type <> ?)
                   AND (? IS NULL OR pb.period = ?)
                   AND (? IS NULL OR pb.branch_id = ?)
                   AND (? IS NULL OR pb.staff_id = ?)
                   AND (? IS NULL OR pb.contract_id = ?)
                """;
        List<Object> args = new ArrayList<>();
        args.add(ruleTypeText); args.add(ruleTypeText);
        args.add(excludeRuleTypeText); args.add(excludeRuleTypeText);
        args.add(periodText); args.add(periodText);
        args.add(branchId); args.add(branchId);
        args.add(staffId); args.add(staffId);
        args.add(contractId); args.add(contractId);

        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;

        List<Object> selectArgs = new ArrayList<>(args);
        selectArgs.add(size);
        selectArgs.add(pageNumber * size);
        List<Map<String, Object>> rows = jdbc.queryForList(listSql()
                + where + " ORDER BY pb.created_at DESC, pb.bonus_id DESC LIMIT ? OFFSET ?", selectArgs.toArray());

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM performance_bonuses pb" + where, Long.class, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    private String listSql() {
        return """
                SELECT pb.*, s.staff_name, b.branch_name, br.rule_name,
                       c.customer_id, cu.company_name
                FROM performance_bonuses pb
                LEFT JOIN staff s ON s.staff_id = pb.staff_id
                LEFT JOIN branches b ON b.branch_id = pb.branch_id
                LEFT JOIN bonus_rules br ON br.bonus_rule_id = pb.bonus_rule_id
                LEFT JOIN contracts c ON c.contract_id = pb.contract_id
                LEFT JOIN customers cu ON cu.customer_id = c.customer_id
                """;
    }

    // ---------- 規則一二三八：逐筆合約／收款觸發 ----------

    public Map<String, Object> syncTransactionBonuses(SyncTransactionBonusRequest request) {
        requireManager(request.staffId());

        int createdCount = 0;
        int skippedAlreadyRecorded = 0;
        int skippedMissingDate = 0;
        List<String> skippedNoActiveRule = new ArrayList<>();

        Set<String> existingContractRule = new HashSet<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT contract_id, rule_type FROM performance_bonuses
                WHERE contract_id IS NOT NULL
                  AND rule_type IN ('OFFICE_RENTAL', 'COMPANY_REGISTRATION', 'TEAMWORK')
                """)) {
            existingContractRule.add(toLong(row.get("contract_id")) + ":" + row.get("rule_type"));
        }
        Set<Long> existingAnnualPaymentIds = new HashSet<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT rent_payment_id FROM performance_bonuses
                WHERE rule_type = 'ANNUAL_PAYMENT' AND rent_payment_id IS NOT NULL
                """)) {
            existingAnnualPaymentIds.add(toLong(row.get("rent_payment_id")));
        }

        // 規則一：辦公室出租獎金
        Map<String, Object> officeRule = optionalSingleActiveRule("OFFICE_RENTAL");
        if (officeRule == null) {
            skippedNoActiveRule.add("OFFICE_RENTAL");
        } else {
            BigDecimal unitAmount = (BigDecimal) officeRule.get("unit_amount");
            Long officeRuleId = toLong(officeRule.get("bonus_rule_id"));
            for (Map<String, Object> row : jdbc.queryForList("""
                    SELECT contract_id, start_date_text, end_date_text, signer_staff_id, partner_staff_id
                    FROM contracts WHERE rental_item = '辦公室' AND lease_status = '綁約中'
                    """)) {
                Long contractId = toLong(row.get("contract_id"));
                if (existingContractRule.contains(contractId + ":OFFICE_RENTAL")) {
                    skippedAlreadyRecorded++;
                    continue;
                }
                LocalDate start = localDate((String) row.get("start_date_text"));
                LocalDate end = localDate((String) row.get("end_date_text"));
                if (start == null || end == null) {
                    skippedMissingDate++;
                    continue;
                }
                long months = Period.between(start, end.plusDays(1)).toTotalMonths();
                if (months < 6 || unitAmount == null) {
                    continue;
                }
                Long signerId = toLong(row.get("signer_staff_id"));
                Long partnerId = toLong(row.get("partner_staff_id"));
                if (signerId == null) {
                    continue;
                }
                String period = YearMonth.from(start).toString();
                if (partnerId != null && !partnerId.equals(signerId)) {
                    BigDecimal half = unitAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    insertBonusRow(signerId, officeRuleId, "OFFICE_RENTAL", contractId, null, null,
                            period, null, null, half, "辦公室出租獎金（雙祕書合作）", request.staffId());
                    insertBonusRow(partnerId, officeRuleId, "OFFICE_RENTAL", contractId, null, null,
                            period, null, null, half, "辦公室出租獎金（雙祕書合作）", request.staffId());
                    createdCount += 2;
                } else {
                    insertBonusRow(signerId, officeRuleId, "OFFICE_RENTAL", contractId, null, null,
                            period, null, null, unitAmount, "辦公室出租獎金", request.staffId());
                    createdCount += 1;
                }
            }
        }

        // 規則二、三：公司登記業績獎金／同心獎金（同一批登記合約）
        Map<String, Object> registrationRule = optionalSingleActiveRule("COMPANY_REGISTRATION");
        Map<String, Object> teamworkRule = optionalSingleActiveRule("TEAMWORK");
        if (registrationRule == null) {
            skippedNoActiveRule.add("COMPANY_REGISTRATION");
        }
        if (teamworkRule == null) {
            skippedNoActiveRule.add("TEAMWORK");
        }
        if (registrationRule != null || teamworkRule != null) {
            for (Map<String, Object> row : jdbc.queryForList("""
                    SELECT contract_id, start_date_text, signer_staff_id, partner_staff_id
                    FROM contracts WHERE rental_item = '登記' AND lease_status = '綁約中'
                    """)) {
                Long contractId = toLong(row.get("contract_id"));
                Long signerId = toLong(row.get("signer_staff_id"));
                Long partnerId = toLong(row.get("partner_staff_id"));
                String period = yearMonthOf((String) row.get("start_date_text"));
                if (period == null) {
                    skippedMissingDate++;
                    continue;
                }

                if (registrationRule != null && signerId != null) {
                    if (existingContractRule.contains(contractId + ":COMPANY_REGISTRATION")) {
                        skippedAlreadyRecorded++;
                    } else {
                        BigDecimal amount = (BigDecimal) registrationRule.get("unit_amount");
                        if (amount != null) {
                            insertBonusRow(signerId, toLong(registrationRule.get("bonus_rule_id")), "COMPANY_REGISTRATION",
                                    contractId, null, null, period, null, null, amount, "公司登記業績獎金", request.staffId());
                            createdCount++;
                        }
                    }
                }

                if (teamworkRule != null && partnerId != null && !partnerId.equals(signerId)) {
                    if (existingContractRule.contains(contractId + ":TEAMWORK")) {
                        skippedAlreadyRecorded++;
                    } else {
                        BigDecimal amount = (BigDecimal) teamworkRule.get("unit_amount");
                        if (amount != null) {
                            insertBonusRow(partnerId, toLong(teamworkRule.get("bonus_rule_id")), "TEAMWORK",
                                    contractId, null, null, period, null, null, amount, "同心獎金", request.staffId());
                            createdCount++;
                        }
                    }
                }
            }
        }

        // 規則八：公司登記年繳獎金
        Map<String, Object> annualRule = optionalSingleActiveRule("ANNUAL_PAYMENT");
        if (annualRule == null) {
            skippedNoActiveRule.add("ANNUAL_PAYMENT");
        } else {
            BigDecimal percentage = (BigDecimal) annualRule.get("percentage");
            Long annualRuleId = toLong(annualRule.get("bonus_rule_id"));
            for (Map<String, Object> row : jdbc.queryForList("""
                    SELECT rp.rent_payment_id, rp.contract_id, rp.amount, rp.payment_date_text, c.signer_staff_id
                    FROM rent_payments rp
                    JOIN contracts c ON c.contract_id = rp.contract_id
                    WHERE c.rental_item = '登記' AND c.payment_months = 12
                      AND rp.payment_date_text IS NOT NULL AND rp.payment_date_text <> ''
                    """)) {
                Long rentPaymentId = toLong(row.get("rent_payment_id"));
                if (existingAnnualPaymentIds.contains(rentPaymentId)) {
                    skippedAlreadyRecorded++;
                    continue;
                }
                String period = yearMonthOf((String) row.get("payment_date_text"));
                if (period == null) {
                    skippedMissingDate++;
                    continue;
                }
                BigDecimal amount = (BigDecimal) row.get("amount");
                Long signerId = toLong(row.get("signer_staff_id"));
                if (amount == null || percentage == null || signerId == null) {
                    continue;
                }
                BigDecimal bonusAmount = amount.multiply(percentage).setScale(2, RoundingMode.HALF_UP);
                insertBonusRow(signerId, annualRuleId, "ANNUAL_PAYMENT", toLong(row.get("contract_id")), null,
                        rentPaymentId, period, null, null, bonusAmount, "公司登記年繳獎金", request.staffId());
                createdCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("createdCount", createdCount);
        result.put("skippedAlreadyRecorded", skippedAlreadyRecorded);
        result.put("skippedMissingDate", skippedMissingDate);
        result.put("skippedNoActiveRule", skippedNoActiveRule);
        return result;
    }

    // ---------- 規則四：滿租獎金（按月） ----------

    public Map<String, Object> settleMonthly(SettleMonthlyBonusRequest request) {
        requireManager(request.staffId());

        String yearMonth = blankToNull(request.yearMonth());
        Matcher matcher = yearMonth == null ? null : YEAR_MONTH_PATTERN.matcher(yearMonth);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("yearMonth must be in YYYY-MM format");
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        YearMonth ym = YearMonth.of(year, month);
        int monthStartNum = year * 10000 + month * 100 + 1;
        int monthEndNum = year * 10000 + month * 100 + ym.lengthOfMonth();

        Map<String, Object> rule = requireSingleActiveRule("FULL_OCCUPANCY");
        BigDecimal unitAmount = (BigDecimal) rule.get("unit_amount");
        if (unitAmount == null) {
            throw new IllegalArgumentException("FULL_OCCUPANCY 規則尚未設定 unitAmount");
        }
        Long ruleId = toLong(rule.get("bonus_rule_id"));

        int fullOccupancyCreatedCount = 0;
        List<Long> skippedBranches = new ArrayList<>();

        for (Map<String, Object> branchRow : jdbc.queryForList("SELECT branch_id FROM branches")) {
            Long branchId = toLong(branchRow.get("branch_id"));

            Integer already = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM performance_bonuses
                    WHERE branch_id = ? AND period = ? AND rule_type = 'FULL_OCCUPANCY'
                    """, Integer.class, branchId, yearMonth);
            if (already != null && already > 0) {
                continue;
            }

            Integer totalOffices = jdbc.queryForObject("SELECT COUNT(*) FROM offices WHERE branch_id = ?", Integer.class, branchId);
            if (totalOffices == null || totalOffices == 0) {
                continue;
            }

            Set<Long> occupiedOfficeIds = new HashSet<>();
            for (Map<String, Object> contractRow : jdbc.queryForList("""
                    SELECT c.office_id, c.start_date_text, c.termination_date_text
                    FROM contracts c JOIN offices o ON o.office_id = c.office_id
                    WHERE o.branch_id = ? AND c.lease_status = '綁約中'
                    """, branchId)) {
                Integer startNum = dateNumber((String) contractRow.get("start_date_text"));
                Integer terminationNum = dateNumber((String) contractRow.get("termination_date_text"));
                if (startNum == null || startNum > monthEndNum) {
                    continue;
                }
                if (terminationNum != null && terminationNum <= monthEndNum) {
                    continue;
                }
                occupiedOfficeIds.add(toLong(contractRow.get("office_id")));
            }

            if (occupiedOfficeIds.size() != totalOffices) {
                continue;
            }

            List<Long> staffIds = queryStaffIdsByBranch(branchId);
            if (staffIds.isEmpty()) {
                skippedBranches.add(branchId);
                continue;
            }
            for (Long staffId : staffIds) {
                insertBonusRow(staffId, ruleId, "FULL_OCCUPANCY", null, branchId, null,
                        yearMonth, null, null, unitAmount, "滿租獎金", request.staffId());
                fullOccupancyCreatedCount++;
            }
        }

        // 規則六：公司登記加乘獎金（按月結算，累計範圍是當月）
        Map<String, Object> multiplierRule = requireSingleActiveRule("REGISTRATION_MULTIPLIER");
        Long multiplierRuleId = toLong(multiplierRule.get("bonus_rule_id"));
        List<Tier> tiers = parseTierConfig((String) multiplierRule.get("tier_config"));

        int registrationMultiplierCreatedCount = 0;
        Map<Long, Integer> registrationCountByStaff = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT signer_staff_id, start_date_text FROM contracts
                WHERE rental_item = '登記' AND signer_staff_id IS NOT NULL
                """)) {
            Integer startNum = dateNumber((String) row.get("start_date_text"));
            if (startNum == null || startNum < monthStartNum || startNum > monthEndNum) {
                continue;
            }
            Long staffId = toLong(row.get("signer_staff_id"));
            registrationCountByStaff.merge(staffId, 1, Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : registrationCountByStaff.entrySet()) {
            Long staffId = entry.getKey();
            int count = entry.getValue();
            BigDecimal tierAmount = resolveTierAmount(tiers, count);
            if (tierAmount == null) {
                continue;
            }
            Integer already = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM performance_bonuses
                    WHERE staff_id = ? AND period = ? AND rule_type = 'REGISTRATION_MULTIPLIER'
                    """, Integer.class, staffId, yearMonth);
            if (already != null && already > 0) {
                continue;
            }
            insertBonusRow(staffId, multiplierRuleId, "REGISTRATION_MULTIPLIER", null, null, null,
                    yearMonth, count, null, tierAmount, "公司登記加乘獎金", request.staffId());
            registrationMultiplierCreatedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", yearMonth);
        result.put("fullOccupancyCreatedCount", fullOccupancyCreatedCount);
        result.put("registrationMultiplierCreatedCount", registrationMultiplierCreatedCount);
        result.put("skippedBranches", skippedBranches);
        return result;
    }

    // ---------- 規則七：4 個月期間結算 ----------

    public Map<String, Object> settlePeriod(SettlePeriodBonusRequest request) {
        requireManager(request.staffId());

        String period = blankToNull(request.period());
        int[] range = parsePeriod(period);

        int createdCount = 0;
        List<Long> skippedBranches = new ArrayList<>();

        // 規則七：分館績效獎金
        Map<String, Object> branchRule = requireSingleActiveRule("BRANCH_PERFORMANCE");
        Long branchRuleId = toLong(branchRule.get("bonus_rule_id"));
        BigDecimal unitAmount = (BigDecimal) branchRule.get("unit_amount");
        if (unitAmount == null) {
            throw new IllegalArgumentException("BRANCH_PERFORMANCE 規則尚未設定 unitAmount");
        }

        Map<Long, Integer> signedCountByBranch = new HashMap<>();
        Map<Long, Integer> cancelledCountByBranch = new HashMap<>();
        int unassignedContractCount = 0;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT c.contract_id, c.start_date_text, c.termination_date_text,
                       o.branch_id AS office_branch_id, st.branch_id AS signer_branch_id
                FROM contracts c
                LEFT JOIN offices o ON o.office_id = c.office_id
                LEFT JOIN staff st ON st.staff_id = c.signer_staff_id
                """)) {
            Long branchId = row.get("office_branch_id") != null ? toLong(row.get("office_branch_id")) : toLong(row.get("signer_branch_id"));
            if (branchId == null) {
                unassignedContractCount++;
                continue;
            }
            Integer startNum = dateNumber((String) row.get("start_date_text"));
            if (startNum != null && startNum >= range[0] && startNum <= range[1]) {
                signedCountByBranch.merge(branchId, 1, Integer::sum);
            }
            Integer terminationNum = dateNumber((String) row.get("termination_date_text"));
            if (terminationNum != null && terminationNum >= range[0] && terminationNum <= range[1]) {
                cancelledCountByBranch.merge(branchId, 1, Integer::sum);
            }
        }

        Set<Long> branchIds = new HashSet<>();
        branchIds.addAll(signedCountByBranch.keySet());
        branchIds.addAll(cancelledCountByBranch.keySet());
        for (Long branchId : branchIds) {
            int signed = signedCountByBranch.getOrDefault(branchId, 0);
            int cancelled = cancelledCountByBranch.getOrDefault(branchId, 0);
            int netCount = signed - cancelled;

            Integer already = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM performance_bonuses
                    WHERE branch_id = ? AND period = ? AND rule_type = 'BRANCH_PERFORMANCE'
                    """, Integer.class, branchId, period);
            if (already != null && already > 0) {
                continue;
            }

            List<Long> staffIds = queryStaffIdsByBranch(branchId);
            if (staffIds.isEmpty()) {
                skippedBranches.add(branchId);
                continue;
            }
            BigDecimal totalBonus = unitAmount.multiply(BigDecimal.valueOf(netCount));
            for (Long staffId : staffIds) {
                insertBonusRow(staffId, branchRuleId, "BRANCH_PERFORMANCE", null, branchId, null,
                        period, signed, cancelled, totalBonus, "分館績效獎金", request.staffId());
                createdCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("createdCount", createdCount);
        result.put("skippedBranches", skippedBranches);
        result.put("unassignedContractCount", unassignedContractCount);
        return result;
    }

    // ---------- 規則五等無自動結算引擎的規則：手動登打單筆獎金 ----------

    public Map<String, Object> manualCreate(ManualPerformanceBonusRequest request) {
        requireManager(request.staffId());
        requiredId(request.beneficiaryStaffId(), "beneficiaryStaffId");
        requiredId(request.bonusRuleId(), "bonusRuleId");

        Map<String, Object> rule;
        try {
            rule = jdbc.queryForMap(
                    "SELECT * FROM bonus_rules WHERE bonus_rule_id = ? AND is_active = TRUE", request.bonusRuleId());
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("bonusRuleId 不存在或未啟用");
        }
        String ruleType = (String) rule.get("rule_type");
        if (!MANUALLY_ONLY_RULE_TYPES.contains(ruleType)) {
            throw new IllegalArgumentException(
                    "ruleType=" + ruleType + " 已有自動結算引擎，請使用結算觸發，不支援手動新增");
        }

        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        Long id = insertBonusRow(request.beneficiaryStaffId(), request.bonusRuleId(), ruleType, null, null, null,
                blankToNull(request.period()), null, null, amount, blankToNull(request.note()), request.staffId());

        return jdbc.queryForMap(listSql() + " WHERE pb.bonus_id = ?", id);
    }

    // ---------- helpers ----------

    private Long insertBonusRow(Long staffId, Long bonusRuleId, String ruleType, Long contractId, Long branchId,
                                 Long rentPaymentId, String period, Integer signedCount, Integer cancelledCount,
                                 BigDecimal bonusAmount, String note, Long createdBy) {
        Integer netCount = (signedCount != null && cancelledCount != null) ? signedCount - cancelledCount : null;
        Long id = nextId("performance_bonuses", "bonus_id");
        jdbc.update("""
                INSERT INTO performance_bonuses (
                    bonus_id, staff_id, period, net_count, bonus_amount, bonus_rule_id, rule_type,
                    contract_id, branch_id, rent_payment_id, signed_count, cancelled_count, note, created_by, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                id, staffId, period, netCount, bonusAmount, bonusRuleId, ruleType, contractId, branchId,
                rentPaymentId, signedCount, cancelledCount, note, createdBy);
        return id;
    }

    private List<Long> queryStaffIdsByBranch(Long branchId) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT staff_id FROM staff WHERE branch_id = ?", branchId)) {
            ids.add(toLong(row.get("staff_id")));
        }
        return ids;
    }

    private Map<String, Object> optionalSingleActiveRule(String ruleType) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM bonus_rules WHERE rule_type = ? AND is_active = TRUE", ruleType);
        return rows.size() == 1 ? rows.get(0) : null;
    }

    private Map<String, Object> requireSingleActiveRule(String ruleType) {
        Map<String, Object> rule = optionalSingleActiveRule(ruleType);
        if (rule == null) {
            throw new IllegalArgumentException(ruleType + " 需要恰好一筆啟用中的業績獎金規則，請先至「業績獎金規則」新增或確認設定");
        }
        return rule;
    }

    private int[] parsePeriod(String period) {
        Matcher matcher = period == null ? null : PERIOD_PATTERN.matcher(period);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("period must be in YYYY-P1/YYYY-P2/YYYY-P3 format");
        }
        int year = Integer.parseInt(matcher.group(1));
        int p = Integer.parseInt(matcher.group(2));
        int startMonth = (p - 1) * 4 + 1;
        int endMonth = startMonth + 3;
        int startNum = year * 10000 + startMonth * 100 + 1;
        int endNum = year * 10000 + endMonth * 100 + YearMonth.of(year, endMonth).lengthOfMonth();
        return new int[]{startNum, endNum};
    }

    private List<Tier> parseTierConfig(String tierConfig) {
        if (tierConfig == null || tierConfig.isBlank()) {
            throw new IllegalArgumentException("REGISTRATION_MULTIPLIER 規則尚未設定 tierConfig");
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(tierConfig,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<Tier> tiers = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                int threshold = ((Number) entry.get("threshold")).intValue();
                BigDecimal amount = new BigDecimal(entry.get("amount").toString());
                tiers.add(new Tier(threshold, amount));
            }
            tiers.sort((a, b) -> Integer.compare(b.threshold(), a.threshold()));
            return tiers;
        } catch (Exception e) {
            throw new IllegalArgumentException("tierConfig JSON 格式錯誤: " + e.getMessage());
        }
    }

    private BigDecimal resolveTierAmount(List<Tier> tiers, int count) {
        for (Tier tier : tiers) {
            if (count >= tier.threshold()) {
                return tier.amount();
            }
        }
        return null;
    }

    private record Tier(int threshold, BigDecimal amount) {
    }

    private String yearMonthOf(String dateText) {
        LocalDate date = localDate(dateText);
        return date == null ? null : YearMonth.from(date).toString();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only 主管 can settle performance bonuses");
        }
    }
}
