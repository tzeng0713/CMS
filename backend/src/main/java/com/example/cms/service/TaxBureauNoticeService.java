package com.example.cms.service;

import com.example.cms.dto.TaxBureauNoticeBranchInfo;
import com.example.cms.dto.TaxBureauNoticeGenerateRequest;
import com.example.cms.dto.TaxBureauNoticeSelection;
import com.example.cms.service.support.CmsJdbcSupport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TaxBureauNoticeService extends CmsJdbcSupport {

    private static final String MOVE_OUT = "MOVE_OUT";
    private static final String MOVE_IN = "MOVE_IN";

    public TaxBureauNoticeService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> preview(String yearMonth, String type) {
        YearMonth ym = parseYearMonth(yearMonth);
        String normalizedType = normalizeType(type);

        List<Map<String, Object>> items = new ArrayList<>();
        if (!MOVE_IN.equals(normalizedType)) {
            items.addAll(candidateItems("termination_date_text", MOVE_OUT, ym));
        }
        if (!MOVE_OUT.equals(normalizedType)) {
            items.addAll(candidateItems("start_date_text", MOVE_IN, ym));
        }

        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        List<Map<String, Object>> unassigned = new ArrayList<>();

        for (Map<String, Object> item : items) {
            Object branchId = item.get("branch_id");
            if (branchId == null) {
                unassigned.add(item);
                continue;
            }
            String key = branchId + "|" + item.get("moveType");
            Map<String, Object> group = groups.computeIfAbsent(key, k -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("branchId", branchId);
                g.put("branchName", item.get("branch_name"));
                g.put("branchCompanyName", item.get("branch_company_name"));
                g.put("branchCode", item.get("branch_code"));
                g.put("branchAddress", item.get("branch_address"));
                g.put("moveType", item.get("moveType"));
                g.put("moveTypeLabel", item.get("moveTypeLabel"));
                g.put("items", new ArrayList<Map<String, Object>>());
                return g;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupItems = (List<Map<String, Object>>) group.get("items");
            groupItems.add(item);
        }

        for (Map<String, Object> group : groups.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupItems = (List<Map<String, Object>>) group.get("items");
            groupItems.sort(Comparator
                    .comparing((Map<String, Object> row) -> (LocalDate) row.get("noticeDate"))
                    .thenComparing(row -> (String) row.get("companyName")));
        }

        List<Map<String, Object>> sortedGroups = new ArrayList<>(groups.values());
        sortedGroups.sort(Comparator
                .comparing((Map<String, Object> g) -> String.valueOf(g.get("branchName")))
                .thenComparing(g -> String.valueOf(g.get("moveType"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yearMonth", ym.toString());
        result.put("type", normalizedType);
        result.put("groups", sortedGroups);
        result.put("unassigned", unassigned);
        return result;
    }

    public byte[] generate(TaxBureauNoticeGenerateRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        Map<Long, TaxBureauNoticeBranchInfo> branchInfoMap = new LinkedHashMap<>();
        if (request.branchInfo() != null) {
            for (TaxBureauNoticeBranchInfo info : request.branchInfo()) {
                if (info.branchId() != null) {
                    branchInfoMap.put(info.branchId(), info);
                }
            }
        }

        Map<String, List<Map<String, Object>>> sheetGroups = new LinkedHashMap<>();
        for (TaxBureauNoticeSelection selection : request.items()) {
            requiredId(selection.contractId(), "contractId");
            if (!MOVE_OUT.equals(selection.moveType()) && !MOVE_IN.equals(selection.moveType())) {
                throw new IllegalArgumentException("moveType must be MOVE_OUT or MOVE_IN");
            }
            String dateColumn = MOVE_OUT.equals(selection.moveType()) ? "termination_date_text" : "start_date_text";

            Map<String, Object> row;
            try {
                row = jdbc.queryForMap(("""
                        SELECT co.contract_id, co.%s AS notice_date_text,
                               c.company_name, c.tax_id,
                               b.branch_id, b.branch_name, b.company_name AS branch_company_name,
                               b.branch_code, b.branch_address
                        FROM contracts co
                        JOIN customers c ON c.customer_id = co.customer_id
                        LEFT JOIN offices o ON o.office_id = co.office_id
                        LEFT JOIN branches b ON b.branch_id = o.branch_id
                        WHERE co.contract_id = ?
                        """).formatted(dateColumn), selection.contractId());
            } catch (EmptyResultDataAccessException e) {
                throw new IllegalArgumentException("contractId not found: " + selection.contractId());
            }

            if (row.get("branch_id") == null) {
                throw new IllegalArgumentException("contract " + selection.contractId() + " has no assigned branch/office");
            }
            LocalDate noticeDate = localDate((String) row.get("notice_date_text"));
            if (noticeDate == null) {
                throw new IllegalArgumentException("contract " + selection.contractId() + " is missing a valid date for " + selection.moveType());
            }
            row.put("move_type", selection.moveType());
            row.put("noticeDate", noticeDate);

            long branchId = ((Number) row.get("branch_id")).longValue();
            String key = branchId + "|" + selection.moveType();
            sheetGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            Set<String> usedSheetNames = new LinkedHashSet<>();

            for (Map.Entry<String, List<Map<String, Object>>> entry : sheetGroups.entrySet()) {
                List<Map<String, Object>> rows = entry.getValue();
                rows.sort(Comparator
                        .comparing((Map<String, Object> r) -> (LocalDate) r.get("noticeDate"))
                        .thenComparing(r -> (String) r.get("company_name")));
                Map<String, Object> first = rows.get(0);
                long branchId = ((Number) first.get("branch_id")).longValue();
                TaxBureauNoticeBranchInfo info = branchInfoMap.get(branchId);
                buildSheet(workbook, rows, info, usedSheetNames, titleStyle, bodyStyle, headerStyle);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("failed to generate tax bureau notice workbook", e);
        }
    }

    private void buildSheet(Workbook workbook, List<Map<String, Object>> rows, TaxBureauNoticeBranchInfo info,
                             Set<String> usedSheetNames, CellStyle titleStyle, CellStyle bodyStyle, CellStyle headerStyle) {
        Map<String, Object> first = rows.get(0);
        String moveType = (String) first.get("move_type");
        boolean isMoveOut = MOVE_OUT.equals(moveType);
        String branchName = blankToEmpty((String) first.get("branch_name"));
        String branchCompanyName = blankToEmpty((String) first.get("branch_company_name"));
        String landlordName = branchCompanyName.isEmpty() ? branchName : branchCompanyName;
        String branchCode = blankToEmpty((String) first.get("branch_code"));
        String branchAddress = blankToEmpty((String) first.get("branch_address"));
        String taxOfficeName = info == null ? "" : blankToEmpty(info.taxOfficeName());
        String responsiblePerson = info == null ? "" : blankToEmpty(info.responsiblePerson());
        String contactPhone = info == null ? "" : blankToEmpty(info.contactPhone());

        Sheet sheet = workbook.createSheet(sheetName(branchCode.isEmpty() ? branchName : branchCode, isMoveOut, usedSheetNames));

        int r = 0;
        Row titleRow = sheet.createRow(r++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("         通     報    ");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        writeText(sheet, r++, "受文者 : " + taxOfficeName, bodyStyle);
        writeText(sheet, r++, "發文者 : " + landlordName, bodyStyle);
        writeText(sheet, r++, "發文日 : " + rocSpaced(today()), bodyStyle);
        writeText(sheet, r++, "主  旨 : 通報公司行號" + (isMoveOut ? "已遷離本址" : "遷入本址"), bodyStyle);

        if (isMoveOut) {
            writeText(sheet, r++, "說  明 : ", bodyStyle);
            writeText(sheet, r++, "一.以下公司因租約到期，並已遷離" + branchAddress, bodyStyle);
            writeText(sheet, r++, "二.通報名單如下", bodyStyle);
            r++;
        } else {
            r++;
            writeText(sheet, r++, "名單如下：", bodyStyle);
            r++;
        }

        Row headerRow = sheet.createRow(r++);
        String[] headers = {"No.", "通報日期", "門牌", "公司名稱", "統一編號"};
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }

        int no = 1;
        for (Map<String, Object> row : rows) {
            Row dataRow = sheet.createRow(r++);
            Cell noCell = dataRow.createCell(0);
            noCell.setCellValue(no++);
            noCell.setCellStyle(bodyStyle);
            Cell dateCell = dataRow.createCell(1);
            dateCell.setCellValue(rocSlash((LocalDate) row.get("noticeDate")));
            dateCell.setCellStyle(bodyStyle);
            Cell codeCell = dataRow.createCell(2);
            codeCell.setCellValue(branchCode);
            codeCell.setCellStyle(bodyStyle);
            Cell nameCell = dataRow.createCell(3);
            nameCell.setCellValue(blankToEmpty((String) row.get("company_name")));
            nameCell.setCellStyle(bodyStyle);
            Cell taxIdCell = dataRow.createCell(4);
            taxIdCell.setCellValue(blankToEmpty((String) row.get("tax_id")));
            taxIdCell.setCellStyle(bodyStyle);
        }

        r++;
        writeText(sheet, r++, "公司   :   " + landlordName, bodyStyle);
        writeText(sheet, r++, "負責人   :   " + responsiblePerson, bodyStyle);
        writeText(sheet, r++, "地  址   :   " + branchAddress, bodyStyle);
        writeText(sheet, r++, "電  話   :   " + contactPhone, bodyStyle);

        for (int c = 0; c < 5; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    private void writeText(Sheet sheet, int rowIndex, String text, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private CellStyle titleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 36);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle bodyStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 15);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private String sheetName(String base, boolean isMoveOut, Set<String> usedSheetNames) {
        String cleaned = (base == null || base.isBlank() ? "分館" : base).replaceAll("[\\\\/?*\\[\\]:]", "-");
        String suffix = isMoveOut ? "" : "遷入";
        String candidate = cleaned + suffix;
        if (candidate.length() > 31) {
            candidate = candidate.substring(0, 31);
        }
        String result = candidate;
        int suffixIndex = 2;
        while (usedSheetNames.contains(result)) {
            String tail = "-" + suffixIndex;
            int maxBaseLength = 31 - tail.length();
            result = (candidate.length() > maxBaseLength ? candidate.substring(0, maxBaseLength) : candidate) + tail;
            suffixIndex++;
        }
        usedSheetNames.add(result);
        return result;
    }

    private List<Map<String, Object>> candidateItems(String dateColumn, String moveType, YearMonth ym) {
        List<Map<String, Object>> rows = jdbc.queryForList(("""
                SELECT co.contract_id, co.customer_id, co.%s AS notice_date_text,
                       c.company_name, c.tax_id,
                       o.office_no, b.branch_id, b.branch_name, b.company_name AS branch_company_name,
                       b.branch_code, b.branch_address
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                WHERE co.%s IS NOT NULL AND co.%s <> ''
                """).formatted(dateColumn, dateColumn, dateColumn));

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        for (Map<String, Object> row : rows) {
            LocalDate noticeDate = localDate((String) row.get("notice_date_text"));
            if (noticeDate == null || noticeDate.isBefore(monthStart) || noticeDate.isAfter(monthEnd)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("contractId", row.get("contract_id"));
            item.put("customerId", row.get("customer_id"));
            item.put("companyName", row.get("company_name"));
            item.put("taxId", row.get("tax_id"));
            item.put("officeNo", row.get("office_no"));
            item.put("branch_id", row.get("branch_id"));
            item.put("branch_name", row.get("branch_name"));
            item.put("branch_company_name", row.get("branch_company_name"));
            item.put("branch_code", row.get("branch_code"));
            item.put("branch_address", row.get("branch_address"));
            item.put("moveType", moveType);
            item.put("moveTypeLabel", moveTypeLabel(moveType));
            item.put("noticeDate", noticeDate);
            item.put("noticeDateText", rocSlash(noticeDate));
            result.add(item);
        }
        return result;
    }

    private String moveTypeLabel(String moveType) {
        return MOVE_OUT.equals(moveType) ? "遷離" : "遷入";
    }

    private YearMonth parseYearMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            throw new IllegalArgumentException("yearMonth is required");
        }
        try {
            return YearMonth.parse(yearMonth.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("yearMonth must be in yyyy-MM format");
        }
    }

    private String normalizeType(String type) {
        String text = blankToNull(type);
        if (text == null) {
            return "BOTH";
        }
        String upper = text.toUpperCase();
        if (MOVE_OUT.equals(upper) || MOVE_IN.equals(upper) || "BOTH".equals(upper)) {
            return upper;
        }
        throw new IllegalArgumentException("type must be MOVE_OUT, MOVE_IN or BOTH");
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Taipei"));
    }

    private String rocSpaced(LocalDate date) {
        return (date.getYear() - 1911) + " 年 " + date.getMonthValue() + " 月 " + date.getDayOfMonth() + " 日";
    }

    private String rocSlash(LocalDate date) {
        return (date.getYear() - 1911) + "/" + date.getMonthValue() + "/" + date.getDayOfMonth();
    }
}
