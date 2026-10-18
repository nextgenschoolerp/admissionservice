package com.kvs.admission.service;

import com.kvs.admission.dto.MeritListEntryResponse;
import com.kvs.admission.dto.MeritListResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a merit list as an A4 noticeboard PDF (US-02012). Pure rendering; no persistence.
 */
public final class MeritListPdfRenderer {

    static final int ROWS_PER_PAGE = 32;
    private static final float MARGIN = 50;
    private static final float ROW_HEIGHT = 20;
    private static final float[] COLUMN_X = {MARGIN, MARGIN + 50, MARGIN + 175, MARGIN + 345, MARGIN + 435};
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'");

    private MeritListPdfRenderer() {
    }

    /**
     * @param list      the merit list to print
     * @param fullNames false prints "Aarav S." instead of the full candidate name (default for public display)
     * @return PDF bytes
     */
    public static byte[] render(MeritListResponse list, boolean fullNames) {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        List<MeritListEntryResponse> entries = list.getEntries();
        int pages = Math.max(1, (entries.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        String generated = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(STAMP);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Merit List " + list.getMeritListCode());
            for (int page = 0; page < pages; page++) {
                PDPage pdPage = new PDPage(PDRectangle.A4);
                document.addPage(pdPage);
                float top = pdPage.getMediaBox().getHeight() - MARGIN;
                try (PDPageContentStream cs = new PDPageContentStream(document, pdPage)) {
                    text(cs, bold, 16, MARGIN, top, "Provisional Merit List - Grade " + display(list.getTargetGradeId())
                            + ", " + display(list.getAcademicYearId()));
                    text(cs, regular, 9, MARGIN, top - 18, "Branch: " + list.getBranchId() + "   |   List: " + list.getMeritListCode()
                            + "   |   Weights: entrance test " + percent(list.getEntranceTestWeight())
                            + ", interview " + percent(list.getInterviewWeight()));
                    float y = top - 48;
                    String[] headers = {"Rank", "Application No.", "Candidate", "Weighted Score", "Status"};
                    for (int c = 0; c < headers.length; c++) {
                        text(cs, bold, 10, COLUMN_X[c], y, headers[c]);
                    }
                    cs.moveTo(MARGIN, y - 5);
                    cs.lineTo(pdPage.getMediaBox().getWidth() - MARGIN, y - 5);
                    cs.stroke();
                    y -= ROW_HEIGHT;
                    int from = page * ROWS_PER_PAGE;
                    for (MeritListEntryResponse e : entries.subList(from, Math.min(entries.size(), from + ROWS_PER_PAGE))) {
                        text(cs, regular, 10, COLUMN_X[0], y, String.valueOf(e.getMeritRank()));
                        text(cs, regular, 10, COLUMN_X[1], y, e.getApplicationNumber());
                        text(cs, regular, 10, COLUMN_X[2], y, fullNames ? e.getCandidateName() : maskName(e.getCandidateName()));
                        text(cs, regular, 10, COLUMN_X[3], y, e.getWeightedScore().toPlainString());
                        text(cs, regular, 10, COLUMN_X[4], y, e.isQualified() ? "Qualified" : "Under review");
                        y -= ROW_HEIGHT;
                    }
                    text(cs, regular, 8, MARGIN, MARGIN, "Provisional and subject to document verification. Equal scores share a rank."
                            + "   Generated " + generated + "   Page " + (page + 1) + " of " + pages);
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render merit list PDF", e);
        }
    }

    /** "Aarav Sharma" -> "Aarav S."; single names are kept. */
    static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(winAnsiSafe(font, value == null ? "" : value));
        cs.endText();
    }

    /** Standard-14 fonts only cover WinAnsi; replace anything else so a name never breaks the export. */
    private static String winAnsiSafe(PDType1Font font, String value) {
        StringBuilder sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    private static String display(String id) {
        return id == null ? "" : id.replace("GRADE-", "").replace('_', ' ');
    }

    private static String percent(java.math.BigDecimal weight) {
        return weight.multiply(java.math.BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }
}
