package com.example.cms.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Renders one A4 page (or more, if a branch's candidate list overflows a page) per branch,
 * mirroring the layout of {@code buildSheet} in {@link TaxBureauNoticeService} for the Excel export.
 */
final class TaxBureauNoticePdfRenderer {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 48f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float TITLE_SIZE = 32f;
    private static final float BODY_SIZE = 11f;
    private static final float ROW_HEIGHT = 20f;
    private static final float HEADER_ROW_HEIGHT = 22f;
    private static final float[] COLUMN_WIDTHS = {40f, 85f, 60f, 220f, 90.28f};
    private static final String[] COLUMN_HEADERS = {"No.", "通報日期", "門牌", "公司名稱", "統一編號"};
    private static final int MIN_TABLE_ROWS = 15;

    private final PDDocument document;
    private final PDFont regularFont;
    private final PDFont boldFont;

    private PDPageContentStream content;
    private float y;

    TaxBureauNoticePdfRenderer(PDDocument document) throws IOException {
        this.document = document;
        this.regularFont = loadFont(document, "/fonts/NotoSansTC-Regular.ttf");
        this.boldFont = loadFont(document, "/fonts/NotoSansTC-Bold.ttf");
    }

    private static PDFont loadFont(PDDocument document, String resourcePath) throws IOException {
        try (InputStream in = TaxBureauNoticePdfRenderer.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("missing bundled font resource: " + resourcePath);
            }
            return PDType0Font.load(document, in, true);
        }
    }

    void renderBranch(List<Map<String, Object>> rows, TaxBureauNoticeService.BranchDocFields fields) throws IOException {
        newPage();

        drawCenteredTitle("通　　報");
        y -= (TITLE_SIZE + 12);

        drawLine("受文者 : " + fields.taxOfficeName());
        drawLine("發文者 : " + fields.landlordName());
        drawLine("發文日 : " + rocSpaced(today()));
        drawLine("主  旨 : 通報公司行號已遷離本址");
        drawLine("說  明 : ");
        drawLine("一.以下公司因租約到期，並已遷離" + fields.branchAddress());
        drawLine("二.通報名單如下");
        y -= 8;

        drawTableHeader();

        int totalRows = Math.max(rows.size(), MIN_TABLE_ROWS);
        for (int i = 0; i < totalRows; i++) {
            if (ensureSpace(ROW_HEIGHT)) {
                drawTableHeader();
            }
            String no = String.valueOf(i + 1);
            String date = "";
            String name = "";
            String taxId = "";
            if (i < rows.size()) {
                Map<String, Object> row = rows.get(i);
                date = rocSlash((LocalDate) row.get("noticeDate"));
                name = blankToEmpty((String) row.get("company_name"));
                taxId = blankToEmpty((String) row.get("tax_id"));
            }
            drawTableRow(new String[]{no, date, fields.branchCode(), name, taxId});
        }

        ensureSpace(4 * ROW_HEIGHT + 10);
        y -= 10;
        drawLine("公司   :   " + fields.landlordName());
        drawLine("負責人   :   " + fields.responsiblePerson());
        drawLine("地  址   :   " + fields.branchAddress());
        drawLine("電  話   :   " + fields.contactPhone());
    }

    void finish() throws IOException {
        if (content != null) {
            content.close();
            content = null;
        }
    }

    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        y = PAGE_HEIGHT - MARGIN;
    }

    /** Starts a new page if {@code needed} points don't fit above the bottom margin. Returns true if a new page was started. */
    private boolean ensureSpace(float needed) throws IOException {
        if (y - needed < MARGIN) {
            newPage();
            return true;
        }
        return false;
    }

    private void drawCenteredTitle(String text) throws IOException {
        float width = boldFont.getStringWidth(sanitize(text)) / 1000f * TITLE_SIZE;
        float x = MARGIN + Math.max(0, (CONTENT_WIDTH - width) / 2f);
        content.beginText();
        content.setFont(boldFont, TITLE_SIZE);
        content.newLineAtOffset(x, y);
        content.showText(sanitize(text));
        content.endText();
    }

    private void drawLine(String text) throws IOException {
        content.beginText();
        content.setFont(regularFont, BODY_SIZE);
        content.newLineAtOffset(MARGIN, y);
        content.showText(sanitize(text));
        content.endText();
        y -= ROW_HEIGHT;
    }

    private void drawTableHeader() throws IOException {
        float x = MARGIN;
        float top = y;
        for (int c = 0; c < COLUMN_HEADERS.length; c++) {
            content.addRect(x, top - HEADER_ROW_HEIGHT, COLUMN_WIDTHS[c], HEADER_ROW_HEIGHT);
            content.stroke();
            drawCellText(COLUMN_HEADERS[c], x, top, COLUMN_WIDTHS[c], HEADER_ROW_HEIGHT, boldFont);
            x += COLUMN_WIDTHS[c];
        }
        y -= HEADER_ROW_HEIGHT;
    }

    private void drawTableRow(String[] values) throws IOException {
        float x = MARGIN;
        float top = y;
        for (int c = 0; c < values.length; c++) {
            content.addRect(x, top - ROW_HEIGHT, COLUMN_WIDTHS[c], ROW_HEIGHT);
            content.stroke();
            drawCellText(values[c], x, top, COLUMN_WIDTHS[c], ROW_HEIGHT, regularFont);
            x += COLUMN_WIDTHS[c];
        }
        y -= ROW_HEIGHT;
    }

    private void drawCellText(String text, float cellX, float cellTop, float cellWidth, float cellHeight, PDFont font) throws IOException {
        String clean = sanitize(text);
        if (clean.isEmpty()) {
            return;
        }
        float maxWidth = cellWidth - 8f;
        float fitted = fittedFontSize(font, clean, maxWidth, BODY_SIZE);
        float tx = cellX + 4f;
        float ty = cellTop - cellHeight / 2f - fitted * 0.32f;
        content.beginText();
        content.setFont(font, fitted);
        content.newLineAtOffset(tx, ty);
        content.showText(clean);
        content.endText();
    }

    private float fittedFontSize(PDFont font, String text, float maxWidth, float defaultSize) throws IOException {
        float size = defaultSize;
        float width = font.getStringWidth(text) / 1000f * size;
        while (width > maxWidth && size > 6f) {
            size -= 0.5f;
            width = font.getStringWidth(text) / 1000f * size;
        }
        return size;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Taipei"));
    }

    private static String rocSpaced(LocalDate date) {
        return (date.getYear() - 1911) + " 年 " + date.getMonthValue() + " 月 " + date.getDayOfMonth() + " 日";
    }

    private static String rocSlash(LocalDate date) {
        return (date.getYear() - 1911) + "/" + date.getMonthValue() + "/" + date.getDayOfMonth();
    }
}
