package com.example.cms.service;

import com.example.cms.dto.RentPaymentImportRequest;
import com.example.cms.dto.RentPaymentImportRow;
import com.example.cms.dto.RentPaymentRequest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RentPaymentImportService {
    private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 1_000;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "公司名稱", "租金月份", "繳款日期", "費用起日", "費用迄日", "金額", "收據號碼", "備註");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JdbcTemplate jdbc;
    private final RentPaymentService rentPaymentService;

    public RentPaymentImportService(JdbcTemplate jdbc, RentPaymentService rentPaymentService) {
        this.jdbc = jdbc;
        this.rentPaymentService = rentPaymentService;
    }

    public Map<String, Object> preview(MultipartFile file) {
        validateUpload(file);
        List<RentPaymentImportRow> rows = parseRows(file);
        return previewResult(file.getOriginalFilename(), validateRows(rows));
    }

    @Transactional
    public Map<String, Object> importRows(RentPaymentImportRequest request) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw new IllegalArgumentException("沒有可匯入的資料");
        }

        List<ValidatedRow> rows = validateRows(request.rows());
        ValidatedRow firstInvalid = rows.stream().filter(row -> !row.row().valid()).findFirst().orElse(null);
        if (firstInvalid != null) {
            String rowNumber = firstInvalid.row().rowNumber() == null ? "?" : String.valueOf(firstInvalid.row().rowNumber());
            throw new IllegalArgumentException("第 " + rowNumber + " 列：" + firstInvalid.row().errors().get(0));
        }

        Long updatedBy = request.updatedBy() == null ? 1L : request.updatedBy();
        for (ValidatedRow row : rows) {
            rentPaymentService.createRentPayment(new RentPaymentRequest(
                    row.customerId(),
                    row.contractId(),
                    row.paymentMonthValue(),
                    row.paymentDate().toString(),
                    row.feeStartDate() == null ? "" : row.feeStartDate().toString(),
                    row.feeEndDate() == null ? "" : row.feeEndDate().toString(),
                    row.amount(),
                    blankToEmpty(row.row().receiptNo()),
                    blankToEmpty(row.row().note()),
                    updatedBy));
        }
        return Map.of("createdCount", rows.size());
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("請選擇 Excel 檔案");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("僅支援 .xlsx 檔案");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Excel 檔案不可超過 10 MB");
        }
    }

    private List<RentPaymentImportRow> parseRows(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Excel 沒有可讀取的工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headerColumns = headerColumns(sheet.getRow(sheet.getFirstRowNum()));
            List<String> missingHeaders = REQUIRED_HEADERS.stream()
                    .filter(header -> !headerColumns.containsKey(header))
                    .toList();
            if (!missingHeaders.isEmpty()) {
                throw new IllegalArgumentException("缺少必要欄位：" + String.join("、", missingHeaders));
            }

            List<RentPaymentImportRow> rows = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(Locale.TAIWAN);
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row sheetRow = sheet.getRow(rowIndex);
                if (isBlankRow(sheetRow, headerColumns.values(), formatter)) {
                    continue;
                }
                if (rows.size() == MAX_DATA_ROWS) {
                    throw new IllegalArgumentException("Excel 最多可匯入 1,000 筆資料");
                }
                rows.add(new RentPaymentImportRow(
                        rowIndex + 1,
                        cellText(sheetRow, headerColumns.get("公司名稱"), formatter),
                        cellText(sheetRow, headerColumns.get("租金月份"), formatter),
                        dateCellText(sheetRow, headerColumns.get("繳款日期"), formatter),
                        dateCellText(sheetRow, headerColumns.get("費用起日"), formatter),
                        dateCellText(sheetRow, headerColumns.get("費用迄日"), formatter),
                        cellText(sheetRow, headerColumns.get("金額"), formatter),
                        cellText(sheetRow, headerColumns.get("收據號碼"), formatter),
                        cellText(sheetRow, headerColumns.get("備註"), formatter),
                        null,
                        List.of()));
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("Excel 至少需要一筆資料");
            }
            return rows;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("無法讀取 Excel 檔案");
        }
    }

    private Map<String, Integer> headerColumns(Row headerRow) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        if (headerRow == null) {
            return columns;
        }
        DataFormatter formatter = new DataFormatter(Locale.TAIWAN);
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim();
            if (!header.isEmpty() && !columns.containsKey(header)) {
                columns.put(header, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private boolean isBlankRow(Row row, Iterable<Integer> columnIndexes, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (Integer columnIndex : columnIndexes) {
            if (!cellText(row, columnIndex, formatter).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Row row, Integer columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex == null) {
            return "";
        }
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String dateCellText(Row row, Integer columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex == null) {
            return "";
        }
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private List<ValidatedRow> validateRows(List<RentPaymentImportRow> sourceRows) {
        List<ValidatedRow> validatedRows = new ArrayList<>();
        Set<String> importKeys = new LinkedHashSet<>();
        for (RentPaymentImportRow source : sourceRows) {
            RentPaymentImportRow row = source == null
                    ? new RentPaymentImportRow(null, "", "", "", "", "", "", "", "", null, List.of())
                    : source;
            List<String> errors = new ArrayList<>();
            String companyName = blankToEmpty(row.companyName());
            YearMonth paymentMonth = parsePaymentMonth(row.paymentMonth(), errors);
            LocalDate paymentDate = parseDate(row.paymentDateText(), "繳款日期", true, errors);
            LocalDate feeStartDate = parseDate(row.feeStartDateText(), "費用起日", false, errors);
            LocalDate feeEndDate = parseDate(row.feeEndDateText(), "費用迄日", false, errors);
            BigDecimal amount = parseAmount(row.amount(), errors);

            if (companyName.isEmpty()) {
                errors.add("公司名稱為必填");
            }
            if (feeStartDate != null && feeEndDate != null && feeStartDate.isAfter(feeEndDate)) {
                errors.add("費用起日不可晚於費用迄日");
            }

            Long customerId = null;
            Long contractId = null;
            if (!companyName.isEmpty()) {
                List<Long> customerIds = jdbc.queryForList(
                        "SELECT customer_id FROM customers WHERE TRIM(company_name) = ? ORDER BY customer_id", Long.class, companyName);
                if (customerIds.isEmpty()) {
                    errors.add("找不到客戶");
                } else if (customerIds.size() > 1) {
                    errors.add("公司名稱對應多位客戶");
                } else {
                    customerId = customerIds.get(0);
                    List<Long> contractIds = jdbc.queryForList("""
                            SELECT contract_id
                            FROM contracts
                            WHERE customer_id = ?
                            ORDER BY contract_id DESC
                            LIMIT 1
                            """, Long.class, customerId);
                    if (contractIds.isEmpty()) {
                        errors.add("客戶沒有可對應的租約");
                    } else {
                        contractId = contractIds.get(0);
                    }
                }
            }

            if (errors.isEmpty()) {
                String duplicateKey = duplicateKey(companyName, paymentMonth, paymentDate, amount, row.receiptNo());
                if (!importKeys.add(duplicateKey)) {
                    errors.add("Excel 內有重複的對帳資料");
                } else if (hasExistingPayment(customerId, paymentMonth, paymentDate, amount, row.receiptNo())) {
                    errors.add("已有相同的對帳資料");
                }
            }

            String normalizedAmount = amount == null ? blankToEmpty(row.amount()) : amount.stripTrailingZeros().toPlainString();
            RentPaymentImportRow resultRow = new RentPaymentImportRow(
                    row.rowNumber(),
                    companyName,
                    paymentMonth == null ? blankToEmpty(row.paymentMonth()) : paymentMonth.toString(),
                    paymentDate == null ? blankToEmpty(row.paymentDateText()) : paymentDate.toString(),
                    feeStartDate == null ? blankToEmpty(row.feeStartDateText()) : feeStartDate.toString(),
                    feeEndDate == null ? blankToEmpty(row.feeEndDateText()) : feeEndDate.toString(),
                    normalizedAmount,
                    blankToEmpty(row.receiptNo()),
                    blankToEmpty(row.note()),
                    errors.isEmpty(),
                    List.copyOf(errors));
            validatedRows.add(new ValidatedRow(resultRow, customerId, contractId, paymentMonth, paymentDate, feeStartDate, feeEndDate, amount));
        }
        return validatedRows;
    }

    private Map<String, Object> previewResult(String fileName, List<ValidatedRow> rows) {
        List<RentPaymentImportRow> previewRows = rows.stream().map(ValidatedRow::row).toList();
        long validRows = previewRows.stream().filter(row -> Boolean.TRUE.equals(row.valid())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName == null ? "" : fileName);
        result.put("totalRows", previewRows.size());
        result.put("validRows", validRows);
        result.put("errorRows", previewRows.size() - validRows);
        result.put("rows", previewRows);
        return result;
    }

    private YearMonth parsePaymentMonth(String source, List<String> errors) {
        String text = blankToEmpty(source);
        if (text.isEmpty()) {
            errors.add("租金月份為必填");
            return null;
        }
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException exception) {
            errors.add("租金月份格式錯誤，請使用 YYYY-MM");
            return null;
        }
    }

    private LocalDate parseDate(String source, String fieldName, boolean required, List<String> errors) {
        String text = blankToEmpty(source);
        if (text.isEmpty()) {
            if (required) {
                errors.add(fieldName + "為必填");
            }
            return null;
        }
        try {
            return LocalDate.parse(text, ISO_DATE);
        } catch (DateTimeParseException exception) {
            errors.add(fieldName + "格式錯誤，請使用 YYYY-MM-DD");
            return null;
        }
    }

    private BigDecimal parseAmount(String source, List<String> errors) {
        String text = blankToEmpty(source).replace(",", "");
        if (text.isEmpty()) {
            errors.add("金額為必填");
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(text);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("金額必須大於 0");
                return null;
            }
            return amount;
        } catch (NumberFormatException exception) {
            errors.add("金額格式錯誤");
            return null;
        }
    }

    private boolean hasExistingPayment(Long customerId, YearMonth paymentMonth, LocalDate paymentDate,
                                       BigDecimal amount, String receiptNo) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM rent_payments
                WHERE customer_id = ?
                  AND payment_month = ?
                  AND COALESCE(payment_date_text, '') = ?
                  AND amount = ?
                  AND COALESCE(receipt_no, '') = ?
                """, Integer.class, customerId, paymentMonth.getYear() * 100 + paymentMonth.getMonthValue(),
                paymentDate.toString(), amount, blankToEmpty(receiptNo));
        return matches != null && matches > 0;
    }

    private String duplicateKey(String companyName, YearMonth paymentMonth, LocalDate paymentDate,
                                BigDecimal amount, String receiptNo) {
        return String.join("\u0000", companyName, paymentMonth.toString(), paymentDate.toString(),
                amount.stripTrailingZeros().toPlainString(), blankToEmpty(receiptNo));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record ValidatedRow(
            RentPaymentImportRow row,
            Long customerId,
            Long contractId,
            YearMonth paymentMonth,
            LocalDate paymentDate,
            LocalDate feeStartDate,
            LocalDate feeEndDate,
            BigDecimal amount
    ) {
        private int paymentMonthValue() {
            return paymentMonth.getYear() * 100 + paymentMonth.getMonthValue();
        }
    }
}
