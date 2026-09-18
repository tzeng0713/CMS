package com.example.cms.service;

import com.example.cms.service.support.CmsJdbcSupport;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BackupService extends CmsJdbcSupport {

    private static final List<String> BACKUP_TABLES = List.of(
            "contracts", "rent_payments", "performance_bonuses", "refunds");
    private static final String SPREADSHEET_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String googleServiceAccountJson;
    private final String driveFolderId;

    public BackupService(JdbcTemplate jdbc,
                          @Value("${cms.backup.google-service-account-json:}") String googleServiceAccountJson,
                          @Value("${cms.backup.drive-folder-id:}") String driveFolderId) {
        super(jdbc);
        this.googleServiceAccountJson = googleServiceAccountJson;
        this.driveFolderId = driveFolderId;
    }

    public Map<String, Object> triggerBackup(Long staffId) {
        requireManager(staffId);

        LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        String fileName = "CMS備份_" + createdAt.format(FILE_TIMESTAMP) + ".xlsx";
        Long logId = nextId("backup_logs", "backup_log_id");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("createdAt", createdAt.toString());

        try {
            byte[] workbookBytes = buildWorkbook();
            DriveUploadResult uploaded = uploadToDrive(workbookBytes, fileName);

            jdbc.update("""
                    INSERT INTO backup_logs (
                        backup_log_id, triggered_by, file_name, drive_file_id, drive_url, status, error_message, created_at
                    ) VALUES (?, ?, ?, ?, ?, 'SUCCESS', NULL, ?)
                    """,
                    logId, staffId, fileName, uploaded.fileId(), uploaded.webViewLink(), createdAt);

            result.put("success", true);
            result.put("driveFileId", uploaded.fileId());
            result.put("driveUrl", uploaded.webViewLink());
            result.put("errorMessage", null);
            return result;
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

            jdbc.update("""
                    INSERT INTO backup_logs (
                        backup_log_id, triggered_by, file_name, drive_file_id, drive_url, status, error_message, created_at
                    ) VALUES (?, ?, ?, NULL, NULL, 'FAILED', ?, ?)
                    """,
                    logId, staffId, fileName, errorMessage, createdAt);

            result.put("success", false);
            result.put("driveFileId", null);
            result.put("driveUrl", null);
            result.put("errorMessage", errorMessage);
            return result;
        }
    }

    public List<Map<String, Object>> listLogs() {
        return jdbc.queryForList("""
                SELECT bl.*, s.staff_name AS triggered_by_name
                FROM backup_logs bl
                LEFT JOIN staff s ON s.staff_id = bl.triggered_by
                ORDER BY bl.created_at DESC
                """);
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only 主管 can trigger data backups");
        }
    }

    private byte[] buildWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            for (String table : BACKUP_TABLES) {
                writeSheet(workbook, table, headerStyle);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeSheet(Workbook workbook, String table, CellStyle headerStyle) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table);
        Sheet sheet = workbook.createSheet(table);
        if (rows.isEmpty()) {
            return;
        }
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        int r = 0;
        Row headerRow = sheet.createRow(r++);
        for (int c = 0; c < columns.size(); c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(columns.get(c));
            cell.setCellStyle(headerStyle);
        }
        for (Map<String, Object> row : rows) {
            Row dataRow = sheet.createRow(r++);
            for (int c = 0; c < columns.size(); c++) {
                writeCellValue(dataRow.createCell(c), row.get(columns.get(c)));
            }
        }
        for (int c = 0; c < columns.size(); c++) {
            sheet.autoSizeColumn(c);
        }
    }

    private void writeCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else if (value instanceof Timestamp timestamp) {
            cell.setCellValue(timestamp.toLocalDateTime());
        } else if (value instanceof Date date) {
            cell.setCellValue(date.toLocalDate());
        } else if (value instanceof LocalDateTime localDateTime) {
            cell.setCellValue(localDateTime);
        } else if (value instanceof LocalDate localDate) {
            cell.setCellValue(localDate);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private DriveUploadResult uploadToDrive(byte[] bytes, String fileName) throws Exception {
        if (blankToNull(googleServiceAccountJson) == null) {
            throw new IllegalStateException("尚未設定 Google Drive 服務帳戶金鑰（GOOGLE_DRIVE_SERVICE_ACCOUNT_KEY_JSON）");
        }
        if (blankToNull(driveFolderId) == null) {
            throw new IllegalStateException("尚未設定 Google Drive 備份資料夾 ID（GOOGLE_DRIVE_BACKUP_FOLDER_ID）");
        }

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(googleServiceAccountJson.getBytes(StandardCharsets.UTF_8)))
                .createScoped(List.of(DriveScopes.DRIVE_FILE));

        Drive drive = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("CMS Backup")
                .build();

        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(List.of(driveFolderId));

        ByteArrayContent content = new ByteArrayContent(SPREADSHEET_MIME_TYPE, bytes);
        com.google.api.services.drive.model.File uploaded = drive.files()
                .create(fileMetadata, content)
                .setFields("id, webViewLink")
                .execute();

        return new DriveUploadResult(uploaded.getId(), uploaded.getWebViewLink());
    }

    private record DriveUploadResult(String fileId, String webViewLink) {
    }
}
